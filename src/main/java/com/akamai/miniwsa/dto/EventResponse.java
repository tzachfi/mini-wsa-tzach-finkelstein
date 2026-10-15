package com.akamai.miniwsa.dto;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.model.Severity;
import java.time.Instant;

public record EventResponse(
        Long id,
        String eventId,
        Instant timestamp,
        Integer configId,
        String policyId,
        String clientIp,
        String hostname,
        String path,
        String method,
        Integer statusCode,
        String userAgent,
        RuleResponse rule,
        Action action,
        GeoLocationResponse geoLocation,
        Integer requestSize,
        Integer responseSize,
        Instant receivedAt,
        String attackType,
        Integer threatScore) {

    public record RuleResponse(String id, String name, String message, Severity severity, RuleCategory category) {}

    public record GeoLocationResponse(String country, String city) {}
}
