package com.akamai.miniwsa.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

@Getter
public class EventValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public EventValidationException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    @Schema(name = "FieldError", description = "One domain validation failure")
    public record FieldError(String field, String message) {}
}
