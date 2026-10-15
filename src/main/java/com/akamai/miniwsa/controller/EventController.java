package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.dto.EventResponse;
import com.akamai.miniwsa.exception.EventValidationException;
import com.akamai.miniwsa.exception.InvalidRequestShapeException;
import com.akamai.miniwsa.service.SecurityEventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventController {

    private static final TypeReference<List<EventIngestRequest>> EVENT_LIST_TYPE = new TypeReference<>() {};

    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping("/ingest")
    public ResponseEntity<List<EventResponse>> ingest(@RequestBody JsonNode body) {
        var requests = parseEvents(body);
        validate(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(securityEventService.ingest(requests));
    }

    private List<EventIngestRequest> parseEvents(JsonNode body) {
        if (body == null || body.isNull() || (!body.isObject() && !body.isArray())) {
            throw new InvalidRequestShapeException("Request body must be a JSON object or array");
        }
        try {
            if (body.isArray()) {
                var events = objectMapper.convertValue(body, EVENT_LIST_TYPE);
                return events == null ? List.of() : events;
            }
            return List.of(objectMapper.convertValue(body, EventIngestRequest.class));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestShapeException("Invalid event payload", ex);
        }
    }

    private void validate(List<EventIngestRequest> requests) {
        var errors = new ArrayList<EventValidationException.FieldError>();
        for (var i = 0; i < requests.size(); i++) {
            var index = i;
            validator.validate(requests.get(i)).forEach(violation -> errors.add(
                    new EventValidationException.FieldError(
                            "[" + index + "]." + violation.getPropertyPath(), violation.getMessage())));
        }
        if (!errors.isEmpty()) {
            throw new EventValidationException("Validation failed", errors);
        }
    }
}
