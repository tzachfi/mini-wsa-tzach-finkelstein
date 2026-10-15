package com.akamai.miniwsa.service;

import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.dto.EventResponse;
import com.akamai.miniwsa.model.GeoLocationEmbeddable;
import com.akamai.miniwsa.model.RuleEmbeddable;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    private final SecurityEventRepository securityEventRepository;

    @Transactional
    public List<EventResponse> ingest(List<EventIngestRequest> requests) {
        var receivedAt = Instant.now();
        var entities = requests.stream().map(request -> toEntity(request, receivedAt)).toList();
        var saved = securityEventRepository.saveAll(entities);
        log.info("Ingested {} security event(s)", saved.size());
        return saved.stream().map(this::toResponse).toList();
    }

    private SecurityEvent toEntity(EventIngestRequest request, Instant receivedAt) {
        return SecurityEvent.builder()
                .eventId(request.eventId())
                .timestamp(request.timestamp())
                .configId(request.configId())
                .policyId(request.policyId())
                .clientIp(request.clientIp())
                .hostname(request.hostname())
                .path(request.path())
                .method(request.method())
                .statusCode(request.statusCode())
                .userAgent(request.userAgent())
                .rule(toRule(request.rule()))
                .action(request.action())
                .geoLocation(toGeo(request.geoLocation()))
                .requestSize(request.requestSize())
                .responseSize(request.responseSize())
                .receivedAt(receivedAt)
                .build();
    }

    private RuleEmbeddable toRule(EventIngestRequest.RuleRequest rule) {
        return RuleEmbeddable.builder()
                .id(rule.id())
                .name(rule.name())
                .message(rule.message())
                .severity(rule.severity())
                .category(rule.category())
                .build();
    }

    private GeoLocationEmbeddable toGeo(EventIngestRequest.GeoLocationRequest geo) {
        if (geo == null) {
            return null;
        }
        return GeoLocationEmbeddable.builder().country(geo.country()).city(geo.city()).build();
    }

    private EventResponse toResponse(SecurityEvent event) {
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
