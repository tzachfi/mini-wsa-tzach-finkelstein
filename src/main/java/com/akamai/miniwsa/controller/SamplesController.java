package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.dto.SamplesResponse;
import com.akamai.miniwsa.exception.EventValidationException;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.service.SamplesService;
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
public class SamplesController {

    private final SamplesService samplesService;

    @GetMapping("/samples")
    public SamplesResponse samples(
            @RequestParam(required = false) Integer configId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) RuleCategory category,
            @RequestParam(required = false) Action action,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var errors = new ArrayList<EventValidationException.FieldError>();
        if (limit < 1 || limit > 100) {
            errors.add(new EventValidationException.FieldError("limit", "must be between 1 and 100"));
        }
        if (offset < 0 || offset > 10_000) {
            errors.add(new EventValidationException.FieldError("offset", "must be between 0 and 10000"));
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
