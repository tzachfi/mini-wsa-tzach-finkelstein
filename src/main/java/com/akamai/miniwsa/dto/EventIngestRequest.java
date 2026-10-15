package com.akamai.miniwsa.dto;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.model.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EventIngestRequest(
        @NotBlank @Size(max = 255) String eventId,
        @NotNull Instant timestamp,
        @NotNull Integer configId,
        @Size(max = 255) String policyId,
        @NotBlank @Size(max = 64) String clientIp,
        @Size(max = 255) String hostname,
        @NotBlank @Size(max = 2048) String path,
        @Size(max = 255) String method,
        Integer statusCode,
        @Size(max = 2048) String userAgent,
        @NotNull @Valid RuleRequest rule,
        @NotNull Action action,
        @Valid GeoLocationRequest geoLocation,
        Integer requestSize,
        Integer responseSize) {

    public record RuleRequest(
            @Size(max = 255) String id,
            @Size(max = 255) String name,
            @Size(max = 255) String message,
            @NotNull Severity severity,
            @NotNull RuleCategory category) {}

    public record GeoLocationRequest(@Size(max = 255) String country, @Size(max = 255) String city) {}
}
