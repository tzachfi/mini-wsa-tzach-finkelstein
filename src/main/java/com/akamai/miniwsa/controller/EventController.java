package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.config.IngestProperties;
import com.akamai.miniwsa.dto.ApiProblemDetail;
import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.dto.EventResponse;
import com.akamai.miniwsa.exception.EventValidationException;
import com.akamai.miniwsa.exception.InvalidRequestShapeException;
import com.akamai.miniwsa.service.SecurityEventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
@Tag(name = "Events")
public class EventController {

    private static final TypeReference<List<EventIngestRequest>> EVENT_LIST_TYPE = new TypeReference<>() {};

    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final IngestProperties ingestProperties;

    @PostMapping("/ingest")
    @Operation(
            summary = "Ingest one event or a batch",
            description =
                    "Accepts a single event object or an array. Always returns 201 with a JSON array of stored events. The server sets receivedAt and computes attackType and threatScore.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description =
                    "A single event object or an array of events. Arrays larger than app.ingest.max-batch-size (default 1000) are rejected.")
    @ApiResponse(
            responseCode = "201",
            description = "Stored events in request order. A single ingest is an array of one.",
            content =
                    @Content(array = @ArraySchema(schema = @Schema(implementation = EventResponse.class))))
    @ApiResponse(
            responseCode = "400",
            description =
                    "Validation failed, batch too large, or malformed JSON. Domain failures include errors as {field, message}.",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "An event with this eventId already exists.",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<List<EventResponse>> ingest(@RequestBody JsonNode body) {
        var requests = parseEvents(body);
        if (requests.size() > ingestProperties.maxBatchSize()) {
            throw new EventValidationException(
                    "Batch size exceeds maximum of " + ingestProperties.maxBatchSize(),
                    List.of(new EventValidationException.FieldError(
                            "", "must contain at most " + ingestProperties.maxBatchSize() + " events")));
        }
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
            if (ex.getCause() instanceof JsonMappingException jme) {
                var field = jme.getPath().stream()
                        .map(r -> r.getIndex() >= 0 ? "[" + r.getIndex() + "]" : "." + r.getFieldName())
                        .collect(Collectors.joining())
                        .replaceFirst("^\\.", "");
                if (!body.isArray()) {
                    field = "[0]." + field;
                }
                throw new EventValidationException(
                        "Validation failed",
                        List.of(new EventValidationException.FieldError(field, mappingMessage(jme))));
            }
            throw new InvalidRequestShapeException("Invalid event payload", ex);
        }
    }

    private static String mappingMessage(JsonMappingException jme) {
        if (jme instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            var allowed = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", ", "[", "]"));
            return "must be one of " + allowed;
        }
        return jme.getOriginalMessage();
    }

    private void validate(List<EventIngestRequest> requests) {
        var errors = new ArrayList<EventValidationException.FieldError>();
        for (var i = 0; i < requests.size(); i++) {
            var event = requests.get(i);
            if (event == null) {
                errors.add(new EventValidationException.FieldError("[" + i + "]", "must not be null"));
                continue;
            }
            var index = i;
            validator.validate(event).forEach(violation -> errors.add(
                    new EventValidationException.FieldError(
                            "[" + index + "]." + violation.getPropertyPath(), violation.getMessage())));
        }
        if (!errors.isEmpty()) {
            throw new EventValidationException("Validation failed", errors);
        }
    }
}
