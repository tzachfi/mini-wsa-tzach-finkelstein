package com.akamai.miniwsa.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.stats")
public record StatsProperties(
        @NotNull @DurationMin(nanos = 1) @DurationMax(days = 366) Duration maxRange,
        @Min(1) @Max(32) int queryThreads,
        @Min(0) @Max(64) int queryQueueCapacity) {}
