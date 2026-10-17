package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.config.GeneratorProperties;
import com.akamai.miniwsa.config.IngestProperties;
import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.exception.EventValidationException;
import com.akamai.miniwsa.generator.EventGenerator;
import com.akamai.miniwsa.generator.EventGenerator.GeneratorException;
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
public class DevEventsController {

    public static final String MAX_BATCH_SIZE_HEADER = "X-Max-Batch-Size";
    public static final String SUMMARY_HEADER = "X-Generator-Summary";

    private final GeneratorProperties generatorProperties;
    private final IngestProperties ingestProperties;

    @GetMapping("/events")
    public ResponseEntity<List<EventIngestRequest>> events(
            @RequestParam(defaultValue = "10000") int count,
            @RequestParam(defaultValue = "42") long seed,
            @RequestParam(defaultValue = "24h") String span,
            @RequestParam(required = false) Instant end) {
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
