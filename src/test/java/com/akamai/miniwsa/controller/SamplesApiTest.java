package com.akamai.miniwsa.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
@Testcontainers
class SamplesApiTest {

    private static final String INGEST_URL = "/v1/events/ingest";
    private static final String SAMPLES_URL = "/v1/events/samples";
    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String MID = "2026-06-01T12:00:00Z";
    private static final String TO = "2026-06-02T00:00:00Z";
    private static final String AFTER = "2026-06-03T00:00:00Z";

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
    void equalTimestampsSortByIdDescending() throws Exception {
        ingest(eventJson("evt-same-ts-a", 14227, FROM, "INJECTION", "DENY"));
        ingest(eventJson("evt-same-ts-b", 14227, FROM, "INJECTION", "DENY"));

        mockMvc.perform(get(SAMPLES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventId").value("evt-same-ts-b"))
                .andExpect(jsonPath("$.events[1].eventId").value("evt-same-ts-a"));
    }

    @Test
    void windowIsHalfOpenAndEachBoundWorksAlone() throws Exception {
        ingest(eventJson("evt-before", 14227, "2025-12-31T00:00:00Z", "INJECTION", "DENY"));
        ingest(eventJson("evt-from", 14227, FROM, "INJECTION", "DENY"));
        ingest(eventJson("evt-mid", 14227, MID, "INJECTION", "DENY"));
        ingest(eventJson("evt-to", 14227, TO, "INJECTION", "DENY"));
        ingest(eventJson("evt-after", 14227, AFTER, "INJECTION", "DENY"));

        mockMvc.perform(get(SAMPLES_URL).param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.events[*].eventId").value(contains("evt-mid", "evt-from")));

        mockMvc.perform(get(SAMPLES_URL).param("from", FROM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.events[*].eventId")
                        .value(contains("evt-after", "evt-to", "evt-mid", "evt-from")));

        mockMvc.perform(get(SAMPLES_URL).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.events[*].eventId")
                        .value(contains("evt-mid", "evt-from", "evt-before")));
    }

    @Test
    void configCategoryAndActionFiltersAreAnded() throws Exception {
        ingest(eventJson("evt-match", 14227, FROM, "INJECTION", "DENY"));
        ingest(eventJson("evt-other-cfg", 99999, FROM, "INJECTION", "DENY"));
        ingest(eventJson("evt-other-cat", 14227, FROM, "XSS", "DENY"));
        ingest(eventJson("evt-other-act", 14227, FROM, "INJECTION", "ALERT"));

        mockMvc.perform(get(SAMPLES_URL)
                        .param("configId", "14227")
                        .param("category", "INJECTION")
                        .param("action", "DENY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].eventId").value("evt-match"))
                .andExpect(jsonPath("$.events[0].attackType").value("SQL/Command Injection"));
    }

    @Test
    void secondPageEchoesTotalAndAppliedPaging() throws Exception {
        ingest(eventJson("evt-page-1", 14227, FROM, "INJECTION", "DENY"));
        ingest(eventJson("evt-page-2", 14227, MID, "INJECTION", "DENY"));
        ingest(eventJson("evt-page-3", 14227, TO, "INJECTION", "DENY"));

        mockMvc.perform(get(SAMPLES_URL).param("limit", "2").param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.offset").value(2))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].eventId").value("evt-page-1"));
    }

    @Test
    void offsetPastTheEndReturnsEmptyEventsWithRealTotal() throws Exception {
        ingest(eventJson("evt-over-1", 14227, FROM, "INJECTION", "DENY"));
        ingest(eventJson("evt-over-2", 14227, MID, "INJECTION", "DENY"));
        ingest(eventJson("evt-over-3", 14227, TO, "INJECTION", "DENY"));

        mockMvc.perform(get(SAMPLES_URL).param("offset", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalExact").value(true))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(100))
                .andExpect(jsonPath("$.events", hasSize(0)));
    }

    private void ingest(String body) throws Exception {
        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static String eventJson(String eventId, int configId, String timestamp, String category, String action) {
        return """
                {
                  "eventId": "%s",
                  "timestamp": "%s",
                  "configId": %d,
                  "clientIp": "203.0.113.42",
                  "path": "/api/v1/login",
                  "rule": {
                    "severity": "CRITICAL",
                    "category": "%s"
                  },
                  "action": "%s"
                }
                """
                .formatted(eventId, timestamp, configId, category, action);
    }
}
