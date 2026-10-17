package com.akamai.miniwsa.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.config.GeneratorProperties;
import com.akamai.miniwsa.config.IngestProperties;
import com.akamai.miniwsa.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = DevEventsController.class,
        properties = {"app.generator.enabled=false", "app.ingest.max-batch-size=1000"})
@Import(GlobalExceptionHandler.class)
@EnableConfigurationProperties({GeneratorProperties.class, IngestProperties.class})
class DevEventsControllerDisabledWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generateReturns404WhenDisabled() throws Exception {
        mockMvc.perform(get("/v1/dev/events")).andExpect(status().isNotFound());
    }
}
