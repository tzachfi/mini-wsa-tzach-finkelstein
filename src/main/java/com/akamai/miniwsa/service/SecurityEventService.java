package com.akamai.miniwsa.service;

import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.dto.EventResponse;
import com.akamai.miniwsa.enrichment.AttackTypeClassifier;
import com.akamai.miniwsa.enrichment.RepeatWindowCounter;
import com.akamai.miniwsa.enrichment.ThreatScoreCalculator;
import com.akamai.miniwsa.model.GeoLocationEmbeddable;
import com.akamai.miniwsa.model.RuleEmbeddable;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.repository.IpTimeWindow;
import com.akamai.miniwsa.repository.SecurityEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    private final SecurityEventRepository securityEventRepository;
    private final AttackTypeClassifier attackTypeClassifier;
    private final ThreatScoreCalculator threatScoreCalculator;
    private final RepeatWindowCounter repeatWindowCounter;

    @Transactional
    public List<EventResponse> ingest(List<EventIngestRequest> requests) {
        var receivedAt = Instant.now();
        var entities = requests.stream().map(request -> toEntity(request, receivedAt)).toList();
        enrich(entities);
        var saved = securityEventRepository.saveAll(entities);
        log.info("Ingested {} security event(s)", saved.size());
        return saved.stream().map(this::toResponse).toList();
    }

    private void enrich(List<SecurityEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        var eventsByIp = events.stream().collect(Collectors.groupingBy(SecurityEvent::getClientIp));
        var windows = eventsByIp.entrySet().stream()
                .map(ipEvents -> {
                    var minTs = ipEvents.getValue().stream()
                            .map(SecurityEvent::getTimestamp)
                            .min(Instant::compareTo)
                            .orElseThrow();
                    var maxTs = ipEvents.getValue().stream()
                            .map(SecurityEvent::getTimestamp)
                            .max(Instant::compareTo)
                            .orElseThrow();
                    return new IpTimeWindow(
                            ipEvents.getKey(), minTs.minus(RepeatWindowCounter.REPEAT_WINDOW), maxTs);
                })
                .toList();

        var history = new HashMap<String, List<Instant>>();
        for (var row : securityEventRepository.findTimestampsInWindows(windows)) {
            var ip = (String) row[0];
            history.computeIfAbsent(ip, ignored -> new ArrayList<>()).add((Instant) row[1]);
        }

        var priors = repeatWindowCounter.priorCounts(events, history);
        for (var i = 0; i < events.size(); i++) {
            var event = events.get(i);
            var rule = event.getRule();
            event.setAttackType(attackTypeClassifier.classify(rule.getCategory()));
            event.setThreatScore(threatScoreCalculator.calculate(
                    rule.getSeverity(), event.getAction(), event.getPath(), priors[i]));
        }
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
