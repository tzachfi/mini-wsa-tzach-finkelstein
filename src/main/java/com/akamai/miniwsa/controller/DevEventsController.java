package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.config.GeneratorProperties;
import com.akamai.miniwsa.config.IngestProperties;
import com.akamai.miniwsa.dto.ApiProblemDetail;
import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.exception.EventValidationException;
import com.akamai.miniwsa.generator.EventGenerator;
import com.akamai.miniwsa.generator.EventGenerator.GeneratorException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dev")
@RequiredArgsConstructor
@Tag(name = "Generator")
public class DevEventsController {

    public static final String MAX_BATCH_SIZE_HEADER = "X-Max-Batch-Size";
    public static final String SUMMARY_HEADER = "X-Generator-Summary";

    private final GeneratorProperties generatorProperties;
    private final IngestProperties ingestProperties;

    @GetMapping("/events")
    @Operation(
            summary = "Generate ingest payloads",
            description =
                    "Returns a generated JSON array of ingest payloads and writes nothing. Gated by app.generator.enabled (default true).")
    @ApiResponse(
            responseCode = "200",
            description = "Generated ingest payloads. Nothing is stored.",
            headers = {
                @Header(
                        name = MAX_BATCH_SIZE_HEADER,
                        description = "Live ingest cap (app.ingest.max-batch-size)",
                        schema = @Schema(type = "string", example = "1000")),
                @Header(
                        name = SUMMARY_HEADER,
                        description = "Pinned and returning attackers",
                        schema = @Schema(type = "string"))
            },
            content =
                    @Content(
                            array = @ArraySchema(schema = @Schema(implementation = EventIngestRequest.class))))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid count, span, or end. Domain failures include errors as {field, message}.",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Generator is disabled.", content = @Content)
    public ResponseEntity<List<EventIngestRequest>> events(
            @Parameter(
                            description = "Number of events to generate",
                            schema = @Schema(minimum = "148", maximum = "100000", defaultValue = "10000"))
                    @RequestParam(defaultValue = "10000")
                    int count,
            @Parameter(description = "RNG seed", schema = @Schema(defaultValue = "42"))
                    @RequestParam(defaultValue = "42")
                    long seed,
            @Parameter(
                            description =
                                    "Window length ending at end (compact 24h or ISO-8601 PT24H). Allowed range 69m–31d.",
                            schema = @Schema(defaultValue = "24h", example = "24h"))
                    @RequestParam(defaultValue = "24h")
                    String span,
            @Parameter(
                            description =
                                    "Window end (ISO-8601 with offset). Defaults to now. Must not be in the future.")
                    @RequestParam(required = false)
                    Instant end) {
        if (!generatorProperties.enabled()) {
            return ResponseEntity.notFound().build();
        }

        var errors = new ArrayList<EventValidationException.FieldError>();
        if (count < EventGenerator.MIN_COUNT || count > EventGenerator.MAX_COUNT) {
            errors.add(new EventValidationException.FieldError(
                    "count",
                    "must be between %d and %d".formatted(EventGenerator.MIN_COUNT, EventGenerator.MAX_COUNT)));
        }

        Duration parsedSpan = null;
        try {
            parsedSpan = EventGenerator.parseSpan(span);
            if (parsedSpan.compareTo(EventGenerator.MIN_SPAN) < 0
                    || parsedSpan.compareTo(EventGenerator.MAX_SPAN) > 0) {
                errors.add(new EventValidationException.FieldError("span", "must be between 69m and 31d"));
            }
        } catch (GeneratorException ex) {
            errors.add(new EventValidationException.FieldError("span", ex.getMessage()));
        }

        var resolvedEnd = end == null ? Instant.now() : end;
        if (resolvedEnd.isAfter(Instant.now())) {
            errors.add(new EventValidationException.FieldError("end", "must not be in the future"));
        }

        if (!errors.isEmpty()) {
            throw new EventValidationException("Validation failed", errors);
        }

        var result = EventGenerator.generate(count, seed, parsedSpan, resolvedEnd);
        return ResponseEntity.ok()
                .header(MAX_BATCH_SIZE_HEADER, String.valueOf(ingestProperties.maxBatchSize()))
                .header(SUMMARY_HEADER, String.join(" | ", result.summaryLines()))
                .body(result.events());
    }
}
