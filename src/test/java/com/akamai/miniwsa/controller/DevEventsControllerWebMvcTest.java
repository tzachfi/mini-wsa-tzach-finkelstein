package com.akamai.miniwsa.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.config.GeneratorProperties;
import com.akamai.miniwsa.config.IngestProperties;
import com.akamai.miniwsa.exception.GlobalExceptionHandler;
import com.akamai.miniwsa.generator.EventGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = DevEventsController.class,
        properties = {"app.generator.enabled=true", "app.ingest.max-batch-size=1000"})
@Import(GlobalExceptionHandler.class)
@EnableConfigurationProperties({GeneratorProperties.class, IngestProperties.class})
class DevEventsControllerWebMvcTest {

    private static final String URL = "/v1/dev/events";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generateReturnsArrayAndBatchSizeHeader() throws Exception {
        mockMvc.perform(get(URL)
                        .param("count", String.valueOf(EventGenerator.MIN_COUNT))
                        .param("seed", "42")
                        .param("span", "24h")
                        .param("end", "2026-06-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().string(DevEventsController.MAX_BATCH_SIZE_HEADER, "1000"))
                .andExpect(header().exists(DevEventsController.SUMMARY_HEADER))
                .andExpect(jsonPath("$", hasSize(EventGenerator.MIN_COUNT)))
                .andExpect(jsonPath("$[0].eventId").value("evt-s42-000000"));
    }

    @Test
    void countBelowMinimumReturns400ProblemDetails() throws Exception {
        mockMvc.perform(get(URL).param("count", "147").param("end", "2026-06-02T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("count"));
    }

    @Test
    void shortSpanReturns400ProblemDetails() throws Exception {
        mockMvc.perform(get(URL)
                        .param("count", String.valueOf(EventGenerator.MIN_COUNT))
                        .param("span", "5m")
                        .param("end", "2026-06-02T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("span"));
    }

    @Test
    void overflowingSpanReturns400ProblemDetails() throws Exception {
        mockMvc.perform(get(URL)
                        .param("count", String.valueOf(EventGenerator.MIN_COUNT))
                        .param("span", "99999999999999999999h")
                        .param("end", "2026-06-02T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("span"));
    }

    @Test
    void nonDurationSpanReturns400ProblemDetails() throws Exception {
        mockMvc.perform(get(URL)
                        .param("count", String.valueOf(EventGenerator.MIN_COUNT))
                        .param("span", "abc")
                        .param("end", "2026-06-02T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("span"));
    }

    @Test
    void futureEndReturns400ProblemDetails() throws Exception {
        mockMvc.perform(get(URL)
                        .param("count", String.valueOf(EventGenerator.MIN_COUNT))
                        .param("end", "2099-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("end"));
    }
}
