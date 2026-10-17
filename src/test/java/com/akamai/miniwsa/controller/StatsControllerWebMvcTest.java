package com.akamai.miniwsa.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.config.StatsProperties;
import com.akamai.miniwsa.config.StatsQueryExecutorConfig;
import com.akamai.miniwsa.exception.GlobalExceptionHandler;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import com.akamai.miniwsa.service.StatsService;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
        controllers = StatsController.class,
        properties = {
            "app.stats.max-range=31d",
            "app.stats.query-threads=6",
            "app.stats.query-queue-capacity=6"
        })
@Import({StatsService.class, StatsQueryExecutorConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(StatsProperties.class)
class StatsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecurityEventRepository securityEventRepository;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void invalidQueryReturns400ProblemDetails(String ignoredName, MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").isString());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of(
                        "missing from",
                        get("/v1/stats/summary").param("to", "2026-06-02T00:00:00Z")),
                Arguments.of(
                        "missing to",
                        get("/v1/stats/summary").param("from", "2026-06-01T00:00:00Z")),
                Arguments.of(
                        "unparseable instant",
                        get("/v1/stats/summary")
                                .param("from", "yesterday")
                                .param("to", "2026-06-02T00:00:00Z")),
                Arguments.of(
                        "local time without offset",
                        get("/v1/stats/summary")
                                .param("from", "2026-06-01T00:00:00")
                                .param("to", "2026-06-02T00:00:00Z")),
                Arguments.of(
                        "from equals to",
                        get("/v1/stats/summary")
                                .param("from", "2026-06-01T00:00:00Z")
                                .param("to", "2026-06-01T00:00:00Z")),
                Arguments.of(
                        "from after to",
                        get("/v1/stats/summary")
                                .param("from", "2026-06-02T00:00:00Z")
                                .param("to", "2026-06-01T00:00:00Z")),
                Arguments.of(
                        "range over max",
                        get("/v1/stats/summary")
                                .param("from", "2026-06-01T00:00:00Z")
                                .param("to", "2026-07-02T00:00:01Z")),
                Arguments.of(
                        "timeseries missing interval",
                        get("/v1/stats/timeseries")
                                .param("from", "2026-06-01T00:00:00Z")
                                .param("to", "2026-06-02T00:00:00Z")),
                Arguments.of(
                        "timeseries unknown interval",
                        get("/v1/stats/timeseries")
                                .param("from", "2026-06-01T00:00:00Z")
                                .param("to", "2026-06-02T00:00:00Z")
                                .param("interval", "2m")),
                Arguments.of(
                        "timeseries 1m over 24h",
                        get("/v1/stats/timeseries")
                                .param("from", "2026-06-01T00:00:00Z")
                                .param("to", "2026-06-02T00:00:01Z")
                                .param("interval", "1m")));
    }
}
