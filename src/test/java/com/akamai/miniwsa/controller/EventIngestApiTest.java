package com.akamai.miniwsa.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
                .andExpect(jsonPath("$[0].attackType").value("SQL/Command Injection"))
                .andExpect(jsonPath("$[0].threatScore").value(75))
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
    void ingestAppliesPathBonusForLoginAndAdmin() throws Exception {
        var body =
                """
                [
                  %s,
                  %s
                ]
                """
                        .formatted(
                                eventJson("evt-path-plain-001", "198.51.100.20", "/api/v1/items", "2026-05-20T16:00:00Z"),
                                eventJson(
                                        "evt-path-login-001", "198.51.100.21", "/api/v1/login", "2026-05-20T16:00:00Z"));

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value("evt-path-plain-001"))
                .andExpect(jsonPath("$[0].attackType").value("SQL/Command Injection"))
                .andExpect(jsonPath("$[0].threatScore").value(60))
                .andExpect(jsonPath("$[1].eventId").value("evt-path-login-001"))
                .andExpect(jsonPath("$[1].attackType").value("SQL/Command Injection"))
                .andExpect(jsonPath("$[1].threatScore").value(75));
    }

    @Test
    void ingestRepeatOffenderAcrossRequestsAppliesBonusOnSixthEvent() throws Exception {
        var ip = "198.51.100.30";
        var timestamp = "2026-05-20T16:10:00Z";
        var firstBatch = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> eventJson("evt-repeat-across-%03d".formatted(i), ip, "/api/v1/login", timestamp))
                .collect(Collectors.joining(",\n", "[\n", "\n]"));

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(firstBatch))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].threatScore").value(75))
                .andExpect(jsonPath("$[4].threatScore").value(75));

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-repeat-across-006", ip, "/api/v1/login", timestamp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId").value("evt-repeat-across-006"))
                .andExpect(jsonPath("$[0].attackType").value("SQL/Command Injection"))
                .andExpect(jsonPath("$[0].threatScore").value(90));
    }

    @Test
    void ingestBatchOfTenFromSameIpAppliesRepeatBonusFromSixthEvent() throws Exception {
        var body = IntStream.rangeClosed(1, 10)
                .mapToObj(i ->
                        eventJson("evt-repeat-batch-%03d".formatted(i), "198.51.100.40", "/api/v1/login", "2026-05-20T16:20:00Z"))
                .collect(Collectors.joining(",\n", "[\n", "\n]"));

        var result = mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(10)));

        for (int i = 0; i < 5; i++) {
            result.andExpect(jsonPath("$[%d].eventId".formatted(i)).value("evt-repeat-batch-%03d".formatted(i + 1)))
                    .andExpect(jsonPath("$[%d].attackType".formatted(i)).value("SQL/Command Injection"))
                    .andExpect(jsonPath("$[%d].threatScore".formatted(i)).value(75));
        }
        for (int i = 5; i < 10; i++) {
            result.andExpect(jsonPath("$[%d].eventId".formatted(i)).value("evt-repeat-batch-%03d".formatted(i + 1)))
                    .andExpect(jsonPath("$[%d].attackType".formatted(i)).value("SQL/Command Injection"))
                    .andExpect(jsonPath("$[%d].threatScore".formatted(i)).value(90));
        }
    }

    @Test
    void ingestBatchOverMaxSizeReturns400() throws Exception {
        var body = IntStream.rangeClosed(1, 1001)
                .mapToObj(i -> eventJson("evt-oversize-%04d".formatted(i)))
                .collect(Collectors.joining(",\n", "[\n", "\n]"));

        mockMvc.perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Batch size exceeds maximum of 1000"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].message").value("must contain at most 1000 events"));
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
        return eventJson(eventId, "203.0.113.42", "/api/v1/login", "2026-05-20T14:32:10Z");
    }

    private static String eventJson(String eventId, String clientIp, String path, String timestamp) {
        return """
                {
                  "eventId": "%s",
                  "timestamp": "%s",
                  "configId": 14227,
                  "policyId": "pol_web1",
                  "clientIp": "%s",
                  "hostname": "www.example.com",
                  "path": "%s",
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
                .formatted(eventId, timestamp, clientIp, path);
    }
}
