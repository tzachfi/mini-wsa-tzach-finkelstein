package com.akamai.miniwsa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = StatsQueryExecutorConfig.class)
@EnableConfigurationProperties(StatsProperties.class)
@TestPropertySource(
        properties = {
            "app.stats.max-range=31d",
            "app.stats.query-threads=4",
            "app.stats.query-queue-capacity=3"
        })
class StatsQueryExecutorConfigTest {

    @Autowired
    private ThreadPoolTaskExecutor statsQueryExecutor;

    @AfterEach
    void tearDown() {
        statsQueryExecutor.shutdown();
    }

    @Test
    void usesAbortPolicyAndConfiguredSizes() {
        assertEquals(4, statsQueryExecutor.getCorePoolSize());
        assertEquals(4, statsQueryExecutor.getMaxPoolSize());
        assertEquals(3, statsQueryExecutor.getQueueCapacity());
        assertInstanceOf(
                ThreadPoolExecutor.AbortPolicy.class,
                statsQueryExecutor.getThreadPoolExecutor().getRejectedExecutionHandler());
    }
}
