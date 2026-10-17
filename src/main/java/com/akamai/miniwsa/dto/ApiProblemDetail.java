package com.akamai.miniwsa.dto;

import com.akamai.miniwsa.exception.EventValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ProblemDetail", description = "RFC 7807 problem details")
public record ApiProblemDetail(
        String type,
        String title,
        Integer status,
        String detail,
        String instance,
        @Schema(description = "Present for domain validation failures")
                List<EventValidationException.FieldError> errors) {}
