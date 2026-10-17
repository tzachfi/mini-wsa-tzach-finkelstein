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
class StatsTimeseriesApiIT {

    private static final String INGEST_URL = "/v1/events/ingest";
    private static final String TIMESERIES_URL = "/v1/stats/timeseries";

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
    void alignedFiveMinuteWindowZeroFillsGapsExcludesToAndOtherConfig() throws Exception {
        ingest(eventJson("evt-ts-00", 14227, "2026-06-01T00:00:00Z"));
        ingest(eventJson("evt-ts-10", 14227, "2026-06-01T00:10:00Z"));
        ingest(eventJson("evt-ts-to", 14227, "2026-06-01T00:20:00Z"));
        ingest(eventJson("evt-ts-other", 99999, "2026-06-01T00:00:00Z"));

        mockMvc.perform(get(TIMESERIES_URL)
                        .param("configId", "14227")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-01T00:20:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(14227))
                .andExpect(jsonPath("$.interval").value("5m"))
                .andExpect(jsonPath("$.timeRange.from").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.timeRange.to").value("2026-06-01T00:20:00Z"))
                .andExpect(jsonPath("$.points", hasSize(4)))
                .andExpect(jsonPath("$.points[0].timestamp").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.points[0].count").value(1))
                .andExpect(jsonPath("$.points[1].timestamp").value("2026-06-01T00:05:00Z"))
                .andExpect(jsonPath("$.points[1].count").value(0))
                .andExpect(jsonPath("$.points[2].timestamp").value("2026-06-01T00:10:00Z"))
                .andExpect(jsonPath("$.points[2].count").value(1))
                .andExpect(jsonPath("$.points[3].timestamp").value("2026-06-01T00:15:00Z"))
                .andExpect(jsonPath("$.points[3].count").value(0));
    }

    @Test
    void unalignedWindowStartsAtCalendarBucketBeforeFrom() throws Exception {
        ingest(eventJson("evt-ts-unaligned-a", 14227, "2026-06-01T00:03:00Z"));
        ingest(eventJson("evt-ts-unaligned-b", 14227, "2026-06-01T00:07:00Z"));

        mockMvc.perform(get(TIMESERIES_URL)
                        .param("configId", "14227")
                        .param("from", "2026-06-01T00:02:00Z")
                        .param("to", "2026-06-01T00:12:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(3)))
                .andExpect(jsonPath("$.points[0].timestamp").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.points[0].count").value(1))
                .andExpect(jsonPath("$.points[1].timestamp").value("2026-06-01T00:05:00Z"))
                .andExpect(jsonPath("$.points[1].count").value(1))
                .andExpect(jsonPath("$.points[2].timestamp").value("2026-06-01T00:10:00Z"))
                .andExpect(jsonPath("$.points[2].count").value(0));
    }

    @Test
    void omittedConfigIdCountsEveryConfiguration() throws Exception {
        ingest(eventJson("evt-ts-cfg-a", 14227, "2026-06-01T00:00:00Z"));
        ingest(eventJson("evt-ts-cfg-b", 99999, "2026-06-01T00:00:00Z"));

        mockMvc.perform(get(TIMESERIES_URL)
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-01T00:05:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId", nullValue()))
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].timestamp").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.points[0].count").value(2));
    }

    @Test
    void emptyWindowReturnsZeroFilledBuckets() throws Exception {
        mockMvc.perform(get(TIMESERIES_URL)
                        .param("configId", "14227")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-01T00:15:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(3)))
                .andExpect(jsonPath("$.points[0].timestamp").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.points[0].count").value(0))
                .andExpect(jsonPath("$.points[1].timestamp").value("2026-06-01T00:05:00Z"))
                .andExpect(jsonPath("$.points[1].count").value(0))
                .andExpect(jsonPath("$.points[2].timestamp").value("2026-06-01T00:10:00Z"))
                .andExpect(jsonPath("$.points[2].count").value(0));
    }

    @Test
    void exactly24HoursOfOneMinuteIsAllowed() throws Exception {
        mockMvc.perform(get(TIMESERIES_URL)
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-02T00:00:00Z")
                        .param("interval", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(1440)))
                .andExpect(jsonPath("$.points[0].timestamp").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.points[1439].timestamp").value("2026-06-01T23:59:00Z"));
    }

    @Test
    void oneMinuteWindowOver24HoursIs400() throws Exception {
        mockMvc.perform(get(TIMESERIES_URL)
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-02T00:00:01Z")
                        .param("interval", "1m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").isString());
    }

    private void ingest(String body) throws Exception {
        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static String eventJson(String eventId, int configId, String timestamp) {
        return """
                {
                  "eventId": "%s",
                  "timestamp": "%s",
                  "configId": %d,
                  "clientIp": "203.0.113.42",
                  "path": "/login",
                  "rule": {
                    "severity": "HIGH",
                    "category": "BOT"
                  },
                  "action": "ALERT"
                }
                """
                .formatted(eventId, timestamp, configId);
    }
}
