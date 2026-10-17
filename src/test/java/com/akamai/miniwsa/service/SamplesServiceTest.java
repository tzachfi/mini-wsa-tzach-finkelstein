package com.akamai.miniwsa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.repository.SampleFilter;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class SamplesServiceTest {

    private static final SampleFilter EMPTY_FILTER = new SampleFilter(null, null, null, null, null);

    @Mock
    private SecurityEventRepository securityEventRepository;

    private SamplesService samplesService;

    @BeforeEach
    void setUp() {
        samplesService = new SamplesService(securityEventRepository, Runnable::run);
    }

    @Test
    void shortPageSkipsCountAndSetsTotalToOffsetPlusN() {
        when(securityEventRepository.findPage(EMPTY_FILTER, 0, 20)).thenReturn(List.of(event(1), event(2)));

        var response = samplesService.sample(null, null, null, null, null, 20, 0);

        assertEquals(2, response.events().size());
        assertEquals(2, response.total());
        assertEquals(20, response.limit());
        assertEquals(0, response.offset());
        verify(securityEventRepository, never()).countMatching(any());
    }

    @Test
    void emptyFirstPageSkipsCountAndSetsTotalToZero() {
        when(securityEventRepository.findPage(EMPTY_FILTER, 0, 20)).thenReturn(List.of());

        var response = samplesService.sample(null, null, null, null, null, 20, 0);

        assertTrue(response.events().isEmpty());
        assertEquals(0, response.total());
        verify(securityEventRepository, never()).countMatching(any());
    }

    @Test
    void fullPageRunsCountAndUsesThatTotal() {
        when(securityEventRepository.findPage(EMPTY_FILTER, 0, 2)).thenReturn(List.of(event(1), event(2)));
        when(securityEventRepository.countMatching(EMPTY_FILTER)).thenReturn(10L);

        var response = samplesService.sample(null, null, null, null, null, 2, 0);

        assertEquals(2, response.events().size());
        assertEquals(10, response.total());
        verify(securityEventRepository).countMatching(eq(EMPTY_FILTER));
    }

    @Test
    void emptyPagePastOffsetZeroRunsCount() {
        when(securityEventRepository.findPage(EMPTY_FILTER, 100, 20)).thenReturn(List.of());
        when(securityEventRepository.countMatching(EMPTY_FILTER)).thenReturn(5L);

        var response = samplesService.sample(null, null, null, null, null, 20, 100);

        assertTrue(response.events().isEmpty());
        assertEquals(5, response.total());
        assertEquals(20, response.limit());
        assertEquals(100, response.offset());
        verify(securityEventRepository).countMatching(eq(EMPTY_FILTER));
    }

    @Test
    void saturatingExecutorThrowsTaskRejectedExceptionWithoutQuerying() {
        var samplesService = new SamplesService(securityEventRepository, command -> {
            throw new TaskRejectedException("full", new RejectedExecutionException("full"));
        });

        assertThrows(
                TaskRejectedException.class, () -> samplesService.sample(null, null, null, null, null, 20, 0));

        verify(securityEventRepository, never()).findPage(any(), anyInt(), anyInt());
        verify(securityEventRepository, never()).countMatching(any());
    }

    private static SecurityEvent event(long id) {
        return SecurityEvent.builder()
                .id(id)
                .eventId("evt-" + id)
                .timestamp(Instant.parse("2026-06-01T00:00:00Z"))
                .configId(14227)
                .clientIp("203.0.113.42")
                .path("/api/v1/login")
                .action(Action.DENY)
                .receivedAt(Instant.parse("2026-06-01T00:00:01Z"))
                .attackType("SQL/Command Injection")
                .threatScore(75)
                .build();
    }
}
