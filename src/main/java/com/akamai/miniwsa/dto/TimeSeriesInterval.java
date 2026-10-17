package com.akamai.miniwsa.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.util.Arrays;

@Schema(
        description = "UTC calendar bucket size",
        type = "string",
        allowableValues = {"1m", "5m", "1h"},
        example = "5m")
public enum TimeSeriesInterval {
    ONE_MINUTE("1m", Duration.ofHours(24), 60),
    FIVE_MINUTES("5m", Duration.ofDays(7), 300),
    ONE_HOUR("1h", Duration.ofDays(31), 3600);

    private final String token;
    private final Duration maxRange;
    private final int strideSeconds;

    TimeSeriesInterval(String token, Duration maxRange, int strideSeconds) {
        this.token = token;
        this.maxRange = maxRange;
        this.strideSeconds = strideSeconds;
    }

    @JsonValue
    public String token() {
        return token;
    }

    public Duration maxRange() {
        return maxRange;
    }

    public int strideSeconds() {
        return strideSeconds;
    }

    public static TimeSeriesInterval fromToken(String token) {
        return Arrays.stream(values())
                .filter(interval -> interval.token.equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown interval: " + token));
    }
}
