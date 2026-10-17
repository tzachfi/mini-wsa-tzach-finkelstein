package com.akamai.miniwsa.service;

import com.akamai.miniwsa.dto.EventResponse;
import com.akamai.miniwsa.model.SecurityEvent;

public final class EventResponseMapper {

    private EventResponseMapper() {}

    public static EventResponse toResponse(SecurityEvent event) {
        var rule = event.getRule();
        var geo = event.getGeoLocation();
        return new EventResponse(
                event.getId(),
                event.getEventId(),
                event.getTimestamp(),
                event.getConfigId(),
                event.getPolicyId(),
                event.getClientIp(),
                event.getHostname(),
                event.getPath(),
                event.getMethod(),
                event.getStatusCode(),
                event.getUserAgent(),
                rule == null
                        ? null
                        : new EventResponse.RuleResponse(
                                rule.getId(),
                                rule.getName(),
                                rule.getMessage(),
                                rule.getSeverity(),
                                rule.getCategory()),
                event.getAction(),
                geo == null ? null : new EventResponse.GeoLocationResponse(geo.getCountry(), geo.getCity()),
                event.getRequestSize(),
                event.getResponseSize(),
                event.getReceivedAt(),
                event.getAttackType(),
                event.getThreatScore());
    }
}
