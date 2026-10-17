package com.akamai.miniwsa.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StatsSummaryApiIT {

    private static final String INGEST_URL = "/v1/events/ingest";
    private static final String SUMMARY_URL = "/v1/stats/summary";
    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String TO = "2026-06-02T00:00:00Z";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @BeforeEach
    void clearEvents() {
        securityEventRepository.deleteAllInBatch();
    }

    @Test
    void summaryWithConfigIdExcludesOtherConfigAndComputesAggregates() throws Exception {
        ingest(eventJson("evt-stats-inj-1", 14227, "203.0.113.42", "/api/v1/login", FROM, "CRITICAL", "INJECTION", "DENY"));
        ingest(eventJson("evt-stats-inj-2", 14227, "203.0.113.42", "/api/v1/login", "2026-06-01T12:00:00Z", "CRITICAL", "INJECTION", "DENY"));
        ingest(eventJson("evt-stats-bot-1", 14227, "203.0.113.50", "/api/v1/search", "2026-06-01T13:00:00Z", "HIGH", "BOT", "ALERT"));
        ingest(eventJson("evt-stats-xss-1", 14227, "203.0.113.42", "/admin", "2026-06-01T14:00:00Z", "MEDIUM", "XSS", "MONITOR"));
        ingest(eventJson("evt-stats-other-cfg", 99999, "198.51.100.99", "/api/v1/login", "2026-06-01T15:00:00Z", "CRITICAL", "INJECTION", "DENY"));

        mockMvc.perform(get(SUMMARY_URL)
                        .param("configId", "14227")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(14227))
                .andExpect(jsonPath("$.timeRange.from").value(FROM))
                .andExpect(jsonPath("$.timeRange.to").value(TO))
                .andExpect(jsonPath("$.totalEvents").value(4))
                .andExpect(jsonPath("$.avgThreatScore").value(56.3))
                .andExpect(jsonPath("$.byCategory.INJECTION.count").value(2))
                .andExpect(jsonPath("$.byCategory.INJECTION.avgThreatScore").value(75.0))
                .andExpect(jsonPath("$.byCategory.BOT.count").value(1))
                .andExpect(jsonPath("$.byCategory.BOT.avgThreatScore").value(40.0))
                .andExpect(jsonPath("$.byCategory.XSS.count").value(1))
                .andExpect(jsonPath("$.byCategory.XSS.avgThreatScore").value(35.0))
                .andExpect(jsonPath("$.byCategory.PROTOCOL_VIOLATION.count").value(0))
                .andExpect(jsonPath("$.byCategory.PROTOCOL_VIOLATION.avgThreatScore", nullValue()))
                .andExpect(jsonPath("$.byCategory.DATA_LEAKAGE.count").value(0))
                .andExpect(jsonPath("$.byCategory.DOS.count").value(0))
                .andExpect(jsonPath("$.byCategory.RATE_LIMIT.count").value(0))
                .andExpect(jsonPath("$.byAction.DENY").value(2))
                .andExpect(jsonPath("$.byAction.ALERT").value(1))
                .andExpect(jsonPath("$.byAction.MONITOR").value(1))
                .andExpect(jsonPath("$.topAttackers", hasSize(2)))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("203.0.113.42"))
                .andExpect(jsonPath("$.topAttackers[0].count").value(3))
                .andExpect(jsonPath("$.topAttackers[0].avgThreatScore").value(61.7))
                .andExpect(jsonPath("$.topAttackers[1].clientIp").value("203.0.113.50"))
                .andExpect(jsonPath("$.topAttackers[1].count").value(1))
                .andExpect(jsonPath("$.topAttackers[1].avgThreatScore").value(40.0))
                .andExpect(jsonPath("$.topTargetedPaths", hasSize(3)))
                .andExpect(jsonPath("$.topTargetedPaths[0].path").value("/api/v1/login"))
                .andExpect(jsonPath("$.topTargetedPaths[0].count").value(2))
                .andExpect(jsonPath("$.topTargetedPaths[1].path").value("/admin"))
                .andExpect(jsonPath("$.topTargetedPaths[1].count").value(1))
                .andExpect(jsonPath("$.topTargetedPaths[2].path").value("/api/v1/search"))
                .andExpect(jsonPath("$.topTargetedPaths[2].count").value(1));
    }

    @Test
    void summaryWithoutConfigIdAggregatesAcrossConfigs() throws Exception {
        ingest(eventJson("evt-all-cfg-a", 14227, "203.0.113.42", "/api/v1/login", FROM, "CRITICAL", "INJECTION", "DENY"));
        ingest(eventJson("evt-all-cfg-b", 99999, "198.51.100.99", "/api/v1/login", "2026-06-01T15:00:00Z", "CRITICAL", "INJECTION", "DENY"));

        mockMvc.perform(get(SUMMARY_URL).param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId", nullValue()))
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.avgThreatScore").value(75.0))
                .andExpect(jsonPath("$.byCategory.INJECTION.count").value(2))
                .andExpect(jsonPath("$.topAttackers", hasSize(2)))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("198.51.100.99"))
                .andExpect(jsonPath("$.topAttackers[1].clientIp").value("203.0.113.42"));
    }

    @Test
    void summaryWindowIsHalfOpen() throws Exception {
        ingest(eventJson(
                "evt-bound-from", 14227, "203.0.113.10", "/api/v1/login", FROM, "CRITICAL", "INJECTION", "DENY"));
        ingest(eventJson("evt-bound-to", 14227, "203.0.113.11", "/api/v1/login", TO, "CRITICAL", "INJECTION", "DENY"));

        mockMvc.perform(get(SUMMARY_URL)
                        .param("configId", "14227")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1))
                .andExpect(jsonPath("$.topAttackers", hasSize(1)))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("203.0.113.10"));
    }

    @Test
    void summaryTieBreaksAttackersByAvgScoreThenIp() throws Exception {
        var ts = "2026-06-01T10:00:00Z";
        ingest(eventJson("evt-tie-101", 14227, "198.51.100.101", "/api/v1/login", ts, "CRITICAL", "INJECTION", "DENY"));
        ingest(eventJson("evt-tie-102", 14227, "198.51.100.102", "/api/v1/login", ts, "CRITICAL", "INJECTION", "ALERT"));
        ingest(eventJson("evt-tie-103", 14227, "198.51.100.103", "/api/v1/items", ts, "CRITICAL", "INJECTION", "DENY"));
        ingest(eventJson("evt-tie-104", 14227, "198.51.100.104", "/api/v1/login", ts, "CRITICAL", "INJECTION", "MONITOR"));
        ingest(eventJson("evt-tie-105", 14227, "198.51.100.105", "/api/v1/items", ts, "HIGH", "INJECTION", "DENY"));
        ingest(eventJson("evt-tie-106", 14227, "198.51.100.106", "/api/v1/login", ts, "HIGH", "INJECTION", "MONITOR"));
        ingest(eventJson("evt-tie-107", 14227, "198.51.100.107", "/api/v1/items", ts, "CRITICAL", "INJECTION", "MONITOR"));
        ingest(eventJson("evt-tie-108", 14227, "198.51.100.108", "/api/v1/login", ts, "MEDIUM", "INJECTION", "MONITOR"));
        ingest(eventJson("evt-tie-109", 14227, "198.51.100.109", "/api/v1/items", ts, "HIGH", "INJECTION", "MONITOR"));
        ingest(eventJson("evt-tie-110", 14227, "198.51.100.110", "/api/v1/login", ts, "LOW", "INJECTION", "MONITOR"));
        ingest(eventJson("evt-tie-111", 14227, "198.51.100.111", "/api/v1/items", ts, "LOW", "INJECTION", "ALERT"));
        ingest(eventJson("evt-tie-112", 14227, "198.51.100.112", "/api/v1/items", ts, "LOW", "INJECTION", "MONITOR"));

        mockMvc.perform(get(SUMMARY_URL)
                        .param("configId", "14227")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(12))
                .andExpect(jsonPath("$.topAttackers", hasSize(10)))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("198.51.100.101"))
                .andExpect(jsonPath("$.topAttackers[0].avgThreatScore").value(75.0))
                .andExpect(jsonPath("$.topAttackers[1].clientIp").value("198.51.100.102"))
                .andExpect(jsonPath("$.topAttackers[1].avgThreatScore").value(65.0))
                .andExpect(jsonPath("$.topAttackers[2].clientIp").value("198.51.100.103"))
                .andExpect(jsonPath("$.topAttackers[2].avgThreatScore").value(60.0))
                .andExpect(jsonPath("$.topAttackers[3].clientIp").value("198.51.100.104"))
                .andExpect(jsonPath("$.topAttackers[3].avgThreatScore").value(55.0))
                .andExpect(jsonPath("$.topAttackers[4].clientIp").value("198.51.100.105"))
                .andExpect(jsonPath("$.topAttackers[4].avgThreatScore").value(50.0))
                .andExpect(jsonPath("$.topAttackers[5].clientIp").value("198.51.100.106"))
                .andExpect(jsonPath("$.topAttackers[5].avgThreatScore").value(45.0))
                .andExpect(jsonPath("$.topAttackers[6].clientIp").value("198.51.100.107"))
                .andExpect(jsonPath("$.topAttackers[6].avgThreatScore").value(40.0))
                .andExpect(jsonPath("$.topAttackers[7].clientIp").value("198.51.100.108"))
                .andExpect(jsonPath("$.topAttackers[7].avgThreatScore").value(35.0))
                .andExpect(jsonPath("$.topAttackers[8].clientIp").value("198.51.100.109"))
                .andExpect(jsonPath("$.topAttackers[8].avgThreatScore").value(30.0))
                .andExpect(jsonPath("$.topAttackers[9].clientIp").value("198.51.100.110"))
                .andExpect(jsonPath("$.topAttackers[9].avgThreatScore").value(25.0));
    }

    private void ingest(String body) throws Exception {
        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static String eventJson(
            String eventId,
            int configId,
            String clientIp,
            String path,
            String timestamp,
            String severity,
            String category,
            String action) {
        return """
                {
                  "eventId": "%s",
                  "timestamp": "%s",
                  "configId": %d,
                  "clientIp": "%s",
                  "path": "%s",
                  "rule": {
                    "severity": "%s",
                    "category": "%s"
                  },
                  "action": "%s"
                }
                """
                .formatted(eventId, timestamp, configId, clientIp, path, severity, category, action);
    }
}
