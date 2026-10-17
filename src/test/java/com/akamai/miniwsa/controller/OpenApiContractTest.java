package com.akamai.miniwsa.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsPublishesContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/dev/events'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/events/ingest'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/stats/summary'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/stats/timeseries'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/events/samples'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/events/ingest'].post.requestBody.content['application/json'].schema.oneOf")
                        .isArray())
                .andExpect(jsonPath("$.paths['/v1/events/ingest'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/v1/events/ingest'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/v1/events/ingest'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/v1/stats/summary'].get.responses['503'].headers['Retry-After']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.errors").exists())
                .andExpect(jsonPath("$.components.schemas.TimeSeriesResponse.properties.interval.enum")
                        .value(containsInAnyOrder("1m", "5m", "1h")));
    }
}
