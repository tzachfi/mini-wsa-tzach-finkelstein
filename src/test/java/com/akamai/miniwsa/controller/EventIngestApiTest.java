package com.akamai.miniwsa.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIngestApiTest {

    private static final String INGEST_URL = "/v1/events/ingest";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ingestSingleEventReturns201ArrayWithReceivedAt() throws Exception {
        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-single-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId").value("evt-single-001"))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].receivedAt", notNullValue()))
                .andExpect(jsonPath("$[0].attackType", nullValue()))
                .andExpect(jsonPath("$[0].threatScore", nullValue()))
                .andExpect(jsonPath("$[0].path").value("/api/v1/login"))
                .andExpect(jsonPath("$[0].rule.category").value("INJECTION"));
    }

    @Test
    void ingestBatchReturns201Array() throws Exception {
        var body =
                """
                [
                  %s,
                  %s
                ]
                """
                        .formatted(eventJson("evt-batch-001"), eventJson("evt-batch-002"));

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value("evt-batch-001"))
                .andExpect(jsonPath("$[1].eventId").value("evt-batch-002"))
                .andExpect(jsonPath("$[0].receivedAt", notNullValue()))
                .andExpect(jsonPath("$[1].receivedAt", notNullValue()));
    }

    @Test
    void ingestMissingRequiredFieldReturns400ProblemDetails() throws Exception {
        var body =
                """
                {
                  "eventId": "evt-invalid-001",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 14227,
                  "path": "/api/v1/login",
                  "rule": {
                    "severity": "CRITICAL",
                    "category": "INJECTION"
                  },
                  "action": "DENY"
                }
                """;

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[0].clientIp"));
    }

    @Test
    void ingestOversizedPathReturns400WithFieldError() throws Exception {
        var oversizedPath = "/x".repeat(1025); // 2050 chars > 2048
        var body = eventJson("evt-oversized-path-001").replace("/api/v1/login", oversizedPath);

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[0].path"));
    }

    @Test
    void ingestInvalidActionEnumReturns400WithFieldError() throws Exception {
        var body = eventJson("evt-bad-action-001").replace("\"DENY\"", "\"BLOCK\"");

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[0].action"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of [DENY, ALERT, MONITOR]"));
    }

    @Test
    void ingestInvalidCategoryInBatchReturns400WithArrayIndex() throws Exception {
        var badEvent = eventJson("evt-bad-category-001").replace("\"INJECTION\"", "\"FOO\"");
        var body =
                """
                [
                  %s,
                  %s
                ]
                """
                        .formatted(eventJson("evt-batch-ok-001"), badEvent);

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[1].rule.category"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(
                                "must be one of [INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT]"));
    }

    @Test
    void ingestInvalidTimestampReturns400WithFieldError() throws Exception {
        var body = eventJson("evt-bad-ts-001").replace("2026-05-20T14:32:10Z", "yesterday");

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[0].timestamp"))
                .andExpect(jsonPath("$.errors[0].message", notNullValue()));
    }

    @Test
    void ingestNullElementInBatchReturns400() throws Exception {
        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content("[null]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[0]"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
    }

    @Test
    void ingestNullAlongsideValidEventReturns400() throws Exception {
        var body =
                """
                [
                  %s,
                  null
                ]
                """
                        .formatted(eventJson("evt-null-slot-001"));

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("[1]"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
    }

    @Test
    void ingestDuplicateEventIdReturns409Conflict() throws Exception {
        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-dup-001")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-dup-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("An event with this eventId already exists"));
    }

    private static String eventJson(String eventId) {
        return """
                {
                  "eventId": "%s",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 14227,
                  "policyId": "pol_web1",
                  "clientIp": "203.0.113.42",
                  "hostname": "www.example.com",
                  "path": "/api/v1/login",
                  "method": "POST",
                  "statusCode": 403,
                  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                  "rule": {
                    "id": "950001",
                    "name": "SQL_INJECTION",
                    "message": "SQL Injection Attack Detected",
                    "severity": "CRITICAL",
                    "category": "INJECTION"
                  },
                  "action": "DENY",
                  "geoLocation": {
                    "country": "CN",
                    "city": "Beijing"
                  },
                  "requestSize": 1024,
                  "responseSize": 256
                }
                """
                .formatted(eventId);
    }
}
