package com.akamai.miniwsa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.akamai.miniwsa.config.StatsProperties;
import com.akamai.miniwsa.dto.TimeSeriesInterval;
import com.akamai.miniwsa.exception.StatsQueryException;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import com.akamai.miniwsa.repository.projection.TimeBucketAgg;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTimeseriesTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-02T00:00:00Z");
    private static final Executor SAME_THREAD = Runnable::run;

    @Mock
    private SecurityEventRepository securityEventRepository;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = service(Duration.ofDays(31));
    }

    @Test
    void fromEqualsToDoesNotQuery() {
        var ex = assertThrows(
                StatsQueryException.class,
                () -> statsService.timeseries(14227, FROM, FROM, TimeSeriesInterval.ONE_MINUTE));
        assertEquals("'from' must be before 'to'", ex.getMessage());
        verifyNoInteractions(securityEventRepository);
    }

    @Test
    void fromAfterToDoesNotQuery() {
        var ex = assertThrows(
                StatsQueryException.class,
                () -> statsService.timeseries(14227, TO, FROM, TimeSeriesInterval.ONE_MINUTE));
        assertEquals("'from' must be before 'to'", ex.getMessage());
        verifyNoInteractions(securityEventRepository);
    }

    @Test
    void rangeOverMaxRangeDoesNotQuery() {
        var maxRange = Duration.ofDays(31);
        statsService = service(maxRange);
        var to = FROM.plus(maxRange).plusSeconds(1);

        var ex = assertThrows(
                StatsQueryException.class,
                () -> statsService.timeseries(14227, FROM, to, TimeSeriesInterval.ONE_HOUR));
        assertEquals("Time range exceeds maximum of " + maxRange, ex.getMessage());
        verifyNoInteractions(securityEventRepository);
    }

    @Test
    void oneMinuteOver24HoursDoesNotQueryWhileMaxRangeAllowsIt() {
        var to = FROM.plus(Duration.ofHours(24)).plusSeconds(1);

        var ex = assertThrows(
                StatsQueryException.class,
                () -> statsService.timeseries(14227, FROM, to, TimeSeriesInterval.ONE_MINUTE));
        assertEquals(
                "Time range exceeds maximum of "
                        + TimeSeriesInterval.ONE_MINUTE.maxRange()
                        + " for interval "
                        + TimeSeriesInterval.ONE_MINUTE.token(),
                ex.getMessage());
        verifyNoInteractions(securityEventRepository);
    }

    @Test
    void oneHourOverMaxRangeFailsWithGlobalMessageEvenIfIntervalAllowsIt() {
        var maxRange = Duration.ofDays(7);
        statsService = service(maxRange);
        var to = FROM.plus(maxRange).plusSeconds(1);

        var ex = assertThrows(
                StatsQueryException.class,
                () -> statsService.timeseries(14227, FROM, to, TimeSeriesInterval.ONE_HOUR));
        assertEquals("Time range exceeds maximum of " + maxRange, ex.getMessage());
        verifyNoInteractions(securityEventRepository);
    }

    @Test
    void oneHourOver31DaysFailsWithIntervalMessageWhenMaxRangeIsLarger() {
        var maxRange = Duration.ofDays(40);
        statsService = service(maxRange);
        var to = FROM.plus(TimeSeriesInterval.ONE_HOUR.maxRange()).plusSeconds(1);

        var ex = assertThrows(
                StatsQueryException.class,
                () -> statsService.timeseries(14227, FROM, to, TimeSeriesInterval.ONE_HOUR));
        assertEquals(
                "Time range exceeds maximum of "
                        + TimeSeriesInterval.ONE_HOUR.maxRange()
                        + " for interval "
                        + TimeSeriesInterval.ONE_HOUR.token(),
                ex.getMessage());
        verifyNoInteractions(securityEventRepository);
    }

    @Test
    void exactly24HoursOfOneMinuteSubmitsOnce() {
        var bucket = mock(TimeBucketAgg.class);
        when(bucket.getBucketStart()).thenReturn(FROM);
        when(bucket.getEventCount()).thenReturn(3L);
        when(securityEventRepository.timeSeriesForConfig(14227, FROM, TO, 60)).thenReturn(List.of(bucket));

        var response = statsService.timeseries(14227, FROM, TO, TimeSeriesInterval.ONE_MINUTE);

        verify(securityEventRepository).timeSeriesForConfig(14227, FROM, TO, 60);
        assertEquals(14227, response.configId());
        assertEquals(TimeSeriesInterval.ONE_MINUTE, response.interval());
        assertEquals(FROM, response.timeRange().from());
        assertEquals(TO, response.timeRange().to());
        assertEquals(1, response.points().size());
        assertEquals(FROM, response.points().getFirst().timestamp());
        assertEquals(3, response.points().getFirst().count());
    }

    private StatsService service(Duration maxRange) {
        return new StatsService(securityEventRepository, new StatsProperties(maxRange, 6, 6), SAME_THREAD);
    }
}
