package com.akamai.miniwsa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingest")
public record IngestProperties(int maxBatchSize) {}
