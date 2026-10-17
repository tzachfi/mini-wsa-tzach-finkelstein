package com.akamai.miniwsa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.exception.GlobalExceptionHandler;
import com.akamai.miniwsa.service.StatsService;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StatsController.class)
@Import(GlobalExceptionHandler.class)
class StatsControllerRejectedExecutionWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @Test
    void rejectedExecutionReturns503WithRetryAfter() throws Exception {
        when(statsService.summarize(any(), any(), any()))
                .thenThrow(new TaskRejectedException("full", new RejectedExecutionException("full")));

        mockMvc.perform(get("/v1/stats/summary")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-02T00:00:00Z"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void rejectedTimeseriesReturns503WithRetryAfter() throws Exception {
        when(statsService.timeseries(any(), any(), any(), any()))
                .thenThrow(new TaskRejectedException("full", new RejectedExecutionException("full")));

        mockMvc.perform(get("/v1/stats/timeseries")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-02T00:00:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").isString());
    }
}
