package com.akamai.miniwsa.service;

import com.akamai.miniwsa.dto.SamplesResponse;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.repository.SampleFilter;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SamplesService {

    private final SecurityEventRepository securityEventRepository;
    private final Executor statsQueryExecutor;

    public SamplesResponse sample(
            Integer configId,
            Instant from,
            Instant to,
            RuleCategory category,
            Action action,
            int limit,
            int offset) {
        var filter = new SampleFilter(configId, from, to, category, action);
        return join(CompletableFuture.supplyAsync(() -> load(filter, limit, offset), statsQueryExecutor));
    }

    private SamplesResponse load(SampleFilter filter, int limit, int offset) {
        var page = securityEventRepository.findPage(filter, offset, limit);
        var n = page.size();
        long total;
        boolean totalExact;
        if (n == limit || (n == 0 && offset > 0)) {
            var counted = securityEventRepository.countMatching(filter, SamplesPaging.COUNT_CAP);
            totalExact = counted <= SamplesPaging.COUNT_CAP;
            total = totalExact ? counted : SamplesPaging.COUNT_CAP;
        } else {
            total = (long) offset + n;
            totalExact = true;
        }
        return new SamplesResponse(
                page.stream().map(EventResponseMapper::toResponse).toList(),
                total,
                totalExact,
                limit,
                offset);
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
}
