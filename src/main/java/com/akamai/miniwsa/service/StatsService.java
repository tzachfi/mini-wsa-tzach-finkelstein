package com.akamai.miniwsa.service;

import com.akamai.miniwsa.config.StatsProperties;
import com.akamai.miniwsa.dto.StatsSummaryResponse;
import com.akamai.miniwsa.exception.StatsQueryException;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import com.akamai.miniwsa.repository.projection.ActionAgg;
import com.akamai.miniwsa.repository.projection.CategoryActionAgg;
import com.akamai.miniwsa.repository.projection.CategoryAgg;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final Limit TOP_N = Limit.of(10);

    private final SecurityEventRepository securityEventRepository;
    private final StatsProperties statsProperties;
    private final Executor statsQueryExecutor;

    public StatsSummaryResponse summarize(Integer configId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new StatsQueryException("'from' must be before 'to'");
        }
        var range = Duration.between(from, to);
        if (range.compareTo(statsProperties.maxRange()) > 0) {
            throw new StatsQueryException("Time range exceeds maximum of " + statsProperties.maxRange());
        }

        var submitted = new ArrayList<CompletableFuture<?>>();
        try {
            var buckets = submit(
                    submitted,
                    () -> configId == null
                            ? securityEventRepository.aggregateByCategoryAndAction(from, to)
                            : securityEventRepository.aggregateByCategoryAndActionForConfig(configId, from, to));
            var attackers = submit(
                    submitted,
                    () -> configId == null
                            ? securityEventRepository.topAttackers(from, to, TOP_N)
                            : securityEventRepository.topAttackersForConfig(configId, from, to, TOP_N));
            var paths = submit(
                    submitted,
                    () -> configId == null
                            ? securityEventRepository.topTargetedPaths(from, to, TOP_N)
                            : securityEventRepository.topTargetedPathsForConfig(configId, from, to, TOP_N));
            return StatsRollup.assemble(
                    configId,
                    from,
                    to,
                    categoriesFrom(join(buckets)),
                    actionsFrom(join(buckets)),
                    join(attackers),
                    join(paths));
        } catch (TaskRejectedException ex) {
            submitted.forEach(future -> future.cancel(true));
            throw ex;
        }
    }

    private <T> CompletableFuture<T> submit(List<CompletableFuture<?>> submitted, Supplier<T> query) {
        var future = CompletableFuture.supplyAsync(query, statsQueryExecutor);
        submitted.add(future);
        return future;
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            var cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static List<CategoryAgg> categoriesFrom(List<CategoryActionAgg> buckets) {
        var byCategory = new EnumMap<RuleCategory, long[]>(RuleCategory.class);
        for (var bucket : buckets) {
            var totals = byCategory.computeIfAbsent(bucket.category(), ignored -> new long[2]);
            totals[0] += nz(bucket.count());
            totals[1] += nz(bucket.threatScoreSum());
        }
        var rows = new ArrayList<CategoryAgg>();
        byCategory.forEach((category, totals) -> rows.add(new CategoryAgg(category, totals[0], totals[1])));
        return rows;
    }

    private static List<ActionAgg> actionsFrom(List<CategoryActionAgg> buckets) {
        var byAction = new EnumMap<Action, Long>(Action.class);
        for (var bucket : buckets) {
            byAction.merge(bucket.action(), nz(bucket.count()), Long::sum);
        }
        var rows = new ArrayList<ActionAgg>();
        byAction.forEach((action, count) -> rows.add(new ActionAgg(action, count)));
        return rows;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
