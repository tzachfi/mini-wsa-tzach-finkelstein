package com.akamai.miniwsa.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.akamai.miniwsa.config.StatsProperties;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class StatsServiceRejectionTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-02T00:00:00Z");

    @Mock
    private SecurityEventRepository securityEventRepository;

    @Test
    void saturatingExecutorCancelsAcceptedWorkAndThrowsTaskRejectedException() {
        var accepted = new AtomicReference<Runnable>();
        Executor executor = command -> {
            if (accepted.compareAndSet(null, command)) {
                return;
            }
            throw new TaskRejectedException("full", new RejectedExecutionException("full"));
        };
        var statsService = new StatsService(
                securityEventRepository, new StatsProperties(Duration.ofDays(31), 6, 6), executor);

        assertThrows(TaskRejectedException.class, () -> statsService.summarize(14227, FROM, TO));

        accepted.get().run();
        verify(securityEventRepository, never()).aggregateByCategoryAndActionForConfig(anyInt(), any(), any());
        verify(securityEventRepository, never()).topAttackersForConfig(anyInt(), any(), any(), any());
        verify(securityEventRepository, never()).topTargetedPathsForConfig(anyInt(), any(), any(), any());
    }
}
