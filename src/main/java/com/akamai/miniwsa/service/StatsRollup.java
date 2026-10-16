package com.akamai.miniwsa.service;

import com.akamai.miniwsa.dto.StatsSummaryResponse;
import com.akamai.miniwsa.dto.StatsSummaryResponse.CategoryStats;
import com.akamai.miniwsa.dto.StatsSummaryResponse.TimeRange;
import com.akamai.miniwsa.dto.StatsSummaryResponse.TopAttacker;
import com.akamai.miniwsa.dto.StatsSummaryResponse.TopTargetedPath;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.repository.projection.ActionAgg;
import com.akamai.miniwsa.repository.projection.AttackerAgg;
import com.akamai.miniwsa.repository.projection.CategoryAgg;
import com.akamai.miniwsa.repository.projection.PathAgg;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public final class StatsRollup {

    private StatsRollup() {}

    public static StatsSummaryResponse assemble(
            Integer configId,
            Instant from,
            Instant to,
            List<CategoryAgg> categoryRows,
            List<ActionAgg> actionRows,
            List<AttackerAgg> attackerRows,
            List<PathAgg> pathRows) {
        var byCategory = new EnumMap<RuleCategory, CategoryStats>(RuleCategory.class);
        for (var category : RuleCategory.values()) {
            byCategory.put(category, new CategoryStats(0, null));
        }

        long totalEvents = 0;
        long totalScoreSum = 0;
        for (var row : categoryRows) {
            var count = nz(row.count());
            var sum = nz(row.threatScoreSum());
            totalEvents += count;
            totalScoreSum += sum;
            byCategory.put(row.category(), new CategoryStats(count, Averages.of(sum, count)));
        }

        var byAction = new EnumMap<Action, Long>(Action.class);
        for (var action : Action.values()) {
            byAction.put(action, 0L);
        }
        for (var row : actionRows) {
            byAction.put(row.action(), nz(row.count()));
        }

        var topAttackers = attackerRows.stream()
                .map(row -> {
                    var count = nz(row.count());
                    return new TopAttacker(row.clientIp(), count, Averages.of(nz(row.threatScoreSum()), count));
                })
                .toList();
        var topPaths = pathRows.stream()
                .map(row -> new TopTargetedPath(row.path(), nz(row.count())))
                .toList();

        return new StatsSummaryResponse(
                configId,
                new TimeRange(from, to),
                totalEvents,
                Averages.of(totalScoreSum, totalEvents),
                Collections.unmodifiableMap(byCategory),
                Collections.unmodifiableMap(byAction),
                topAttackers,
                topPaths);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
