package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.dto.ApiProblemDetail;
import com.akamai.miniwsa.dto.SamplesResponse;
import com.akamai.miniwsa.exception.EventValidationException;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.service.SamplesPaging;
import com.akamai.miniwsa.service.SamplesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class SamplesController {

    private final SamplesService samplesService;

    @GetMapping("/samples")
    @Operation(
            summary = "Page enriched events",
            description =
                    "Returns individual enriched events matching optional filters (AND), newest first. Both from and to together are the same half-open [from, to) window as stats.")
    @ApiResponse(
            responseCode = "200",
            description = "Page of events. totalExact is false when the match set exceeds the 10100-row cap.")
    @ApiResponse(
            responseCode = "400",
            description =
                    "limit outside 1..100, offset outside 0..10000, from not before to, or a value that does not parse.",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Stats and samples query pool is full.",
            headers =
                    @Header(
                            name = "Retry-After",
                            description = "Seconds to wait before retrying",
                            schema = @Schema(type = "string", example = "2")),
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public SamplesResponse samples(
            @Parameter(description = "Equality on configId") @RequestParam(required = false) Integer configId,
            @Parameter(description = "timestamp >= from (ISO-8601 instant with an offset)")
                    @RequestParam(required = false)
                    Instant from,
            @Parameter(description = "timestamp < to (ISO-8601 instant with an offset)")
                    @RequestParam(required = false)
                    Instant to,
            @Parameter(description = "Equality on rule.category") @RequestParam(required = false)
                    RuleCategory category,
            @Parameter(description = "Equality on action") @RequestParam(required = false) Action action,
            @Parameter(
                            description = "Page size",
                            schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
                    @RequestParam(defaultValue = "20")
                    int limit,
            @Parameter(
                            description = "Skip count",
                            schema = @Schema(minimum = "0", maximum = "10000", defaultValue = "0"))
                    @RequestParam(defaultValue = "0")
                    int offset) {
        var errors = new ArrayList<EventValidationException.FieldError>();
        if (limit < 1 || limit > SamplesPaging.MAX_LIMIT) {
            errors.add(new EventValidationException.FieldError(
                    "limit", "must be between 1 and " + SamplesPaging.MAX_LIMIT));
        }
        if (offset < 0 || offset > SamplesPaging.MAX_OFFSET) {
            errors.add(new EventValidationException.FieldError(
                    "offset", "must be between 0 and " + SamplesPaging.MAX_OFFSET));
        }
        if (from != null && to != null && !from.isBefore(to)) {
            errors.add(new EventValidationException.FieldError("from", "'from' must be before 'to'"));
        }
        if (!errors.isEmpty()) {
            throw new EventValidationException("Validation failed", errors);
        }
        return samplesService.sample(configId, from, to, category, action, limit, offset);
    }
}
