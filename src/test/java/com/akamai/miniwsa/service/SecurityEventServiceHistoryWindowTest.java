package com.akamai.miniwsa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.enrichment.AttackTypeClassifier;
import com.akamai.miniwsa.enrichment.RepeatWindowCounter;
import com.akamai.miniwsa.enrichment.ThreatScoreCalculator;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.model.Severity;
import com.akamai.miniwsa.repository.IpTimeWindow;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityEventServiceHistoryWindowTest {

    private static final String IP_A = "198.51.100.1";
    private static final String IP_B = "198.51.100.2";
    private static final Instant TS_A = Instant.parse("2026-05-20T14:00:00Z");
    private static final Instant TS_B = Instant.parse("2026-05-20T16:00:00Z");

    @Mock
    private SecurityEventRepository securityEventRepository;

    private SecurityEventService securityEventService;

    @BeforeEach
    void setUp() {
        securityEventService = new SecurityEventService(
                securityEventRepository,
                new AttackTypeClassifier(),
                new ThreatScoreCalculator(),
                new RepeatWindowCounter());
    }

    @Test
    void enrichIssuesOneQueryWithPerIpWindows() {
        when(securityEventRepository.findTimestampsInWindows(anyList())).thenReturn(List.of());
        when(securityEventRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<SecurityEvent> events = invocation.getArgument(0);
            var id = new AtomicLong(1);
            events.forEach(event -> event.setId(id.getAndIncrement()));
            return events;
        });

        securityEventService.ingest(List.of(request("evt-a", IP_A, TS_A), request("evt-b", IP_B, TS_B)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IpTimeWindow>> windows = ArgumentCaptor.forClass(List.class);
        verify(securityEventRepository, times(1)).findTimestampsInWindows(windows.capture());
        assertThat(windows.getValue())
                .containsExactlyInAnyOrder(
                        new IpTimeWindow(IP_A, Instant.parse("2026-05-20T13:50:00Z"), TS_A),
                        new IpTimeWindow(IP_B, Instant.parse("2026-05-20T15:50:00Z"), TS_B));
    }

    private static EventIngestRequest request(String eventId, String clientIp, Instant timestamp) {
        return new EventIngestRequest(
                eventId,
                timestamp,
                14227,
                "pol_web1",
                clientIp,
                "www.example.com",
                "/api/v1/login",
                "POST",
                403,
                "Mozilla/5.0",
                new EventIngestRequest.RuleRequest("950001", "SQL_INJECTION", "SQL Injection", Severity.CRITICAL, RuleCategory.INJECTION),
                Action.DENY,
                new EventIngestRequest.GeoLocationRequest("CN", "Beijing"),
                1024,
                256);
    }
}
