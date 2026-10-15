package com.akamai.miniwsa.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class EventValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public EventValidationException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public record FieldError(String field, String message) {}
}
