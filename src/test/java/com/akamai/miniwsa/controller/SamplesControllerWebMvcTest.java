package com.akamai.miniwsa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.dto.SamplesResponse;
import com.akamai.miniwsa.exception.GlobalExceptionHandler;
import com.akamai.miniwsa.service.SamplesService;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = SamplesController.class)
@Import(GlobalExceptionHandler.class)
class SamplesControllerWebMvcTest {

    private static final String SAMPLES_URL = "/v1/events/samples";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SamplesService samplesService;

    @BeforeEach
    void stubService() {
        when(samplesService.sample(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new SamplesResponse(List.of(), 0L, 20, 0));
    }

    @Test
    void limitZeroReturns400WithFieldError() throws Exception {
        expectDomainErrors(get(SAMPLES_URL).param("limit", "0"), "limit", "must be between 1 and 100");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void limitOneCallsService() throws Exception {
        mockMvc.perform(get(SAMPLES_URL).param("limit", "1")).andExpect(status().isOk());
        verify(samplesService).sample(null, null, null, null, null, 1, 0);
    }

    @Test
    void limit100CallsService() throws Exception {
        mockMvc.perform(get(SAMPLES_URL).param("limit", "100")).andExpect(status().isOk());
        verify(samplesService).sample(null, null, null, null, null, 100, 0);
    }

    @Test
    void limit101Returns400WithFieldError() throws Exception {
        expectDomainErrors(get(SAMPLES_URL).param("limit", "101"), "limit", "must be between 1 and 100");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void offsetNegativeReturns400WithFieldError() throws Exception {
        expectDomainErrors(
                get(SAMPLES_URL).param("offset", "-1"), "offset", "must be between 0 and 10000");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void offsetZeroCallsService() throws Exception {
        mockMvc.perform(get(SAMPLES_URL).param("offset", "0")).andExpect(status().isOk());
        verify(samplesService).sample(null, null, null, null, null, 20, 0);
    }

    @Test
    void offset10000CallsService() throws Exception {
        mockMvc.perform(get(SAMPLES_URL).param("offset", "10000")).andExpect(status().isOk());
        verify(samplesService).sample(null, null, null, null, null, 20, 10_000);
    }

    @Test
    void offset10001Returns400WithFieldError() throws Exception {
        expectDomainErrors(
                get(SAMPLES_URL).param("offset", "10001"), "offset", "must be between 0 and 10000");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void allDomainViolationsAreListedTogether() throws Exception {
        mockMvc.perform(get(SAMPLES_URL)
                        .param("limit", "0")
                        .param("offset", "-1")
                        .param("from", "2026-06-02T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 1 and 100"))
                .andExpect(jsonPath("$.errors[1].field").value("offset"))
                .andExpect(jsonPath("$.errors[1].message").value("must be between 0 and 10000"))
                .andExpect(jsonPath("$.errors[2].field").value("from"))
                .andExpect(jsonPath("$.errors[2].message").value("'from' must be before 'to'"));
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void nonIntegerLimitReturnsTypeMismatchWithoutErrors() throws Exception {
        expectTypeMismatch(get(SAMPLES_URL).param("limit", "abc"), "limit");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void unknownCategoryReturnsTypeMismatchWithoutErrors() throws Exception {
        expectTypeMismatch(get(SAMPLES_URL).param("category", "NOT_A_CATEGORY"), "category");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void instantWithoutOffsetReturnsTypeMismatchWithoutErrors() throws Exception {
        expectTypeMismatch(get(SAMPLES_URL).param("from", "2026-06-01T00:00:00"), "from");
        verify(samplesService, never()).sample(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void rejectedExecutionReturns503WithRetryAfter() throws Exception {
        when(samplesService.sample(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new TaskRejectedException("full", new RejectedExecutionException("full")));

        mockMvc.perform(get(SAMPLES_URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").isString());
    }

    private ResultActions expectDomainErrors(
            MockHttpServletRequestBuilder request, String field, String message) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(message));
    }

    private ResultActions expectTypeMismatch(MockHttpServletRequestBuilder request, String parameter)
            throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter '%s'".formatted(parameter)))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
