package com.akamai.miniwsa.dto;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.model.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record EventIngestRequest(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        @NotNull Integer configId,
        String policyId,
        @NotBlank String clientIp,
        String hostname,
        @NotBlank String path,
        String method,
        Integer statusCode,
        String userAgent,
        @NotNull @Valid RuleRequest rule,
        @NotNull Action action,
        GeoLocationRequest geoLocation,
        Integer requestSize,
        Integer responseSize) {

    public record RuleRequest(
            String id, String name, String message, @NotNull Severity severity, @NotNull RuleCategory category) {}

    public record GeoLocationRequest(String country, String city) {}
}
