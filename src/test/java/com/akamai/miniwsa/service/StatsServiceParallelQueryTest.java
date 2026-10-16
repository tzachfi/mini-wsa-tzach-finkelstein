package com.akamai.miniwsa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.akamai.miniwsa.config.StatsProperties;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class StatsServiceParallelQueryTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-02T00:00:00Z");
    private static final Limit TOP_N = Limit.of(10);

    @Mock
    private SecurityEventRepository securityEventRepository;

    private ExecutorService executor;
    private StatsService statsService;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(3);
        statsService = new StatsService(
                securityEventRepository, new StatsProperties(Duration.ofDays(31), 6, 6), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void summaryQueriesOverlap() throws Exception {
        var entered = new CountDownLatch(3);
        var release = new CountDownLatch(1);

        when(securityEventRepository.aggregateByCategoryAndActionForConfig(14227, FROM, TO))
                .thenAnswer(invocation -> blockUntilReleased(entered, release));
        when(securityEventRepository.topAttackersForConfig(14227, FROM, TO, TOP_N))
                .thenAnswer(invocation -> blockUntilReleased(entered, release));
        when(securityEventRepository.topTargetedPathsForConfig(14227, FROM, TO, TOP_N))
                .thenAnswer(invocation -> blockUntilReleased(entered, release));

        var result = CompletableFuture.supplyAsync(() -> statsService.summarize(14227, FROM, TO));

        assertTrue(entered.await(5, TimeUnit.SECONDS), "the three aggregate queries did not overlap");
        release.countDown();
        var summary = result.get(5, TimeUnit.SECONDS);
        assertEquals(0, summary.totalEvents());
    }

    private static <T> List<T> blockUntilReleased(CountDownLatch entered, CountDownLatch release)
            throws InterruptedException {
        entered.countDown();
        assertTrue(release.await(5, TimeUnit.SECONDS));
        return List.of();
    }
}
