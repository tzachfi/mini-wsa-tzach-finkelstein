package com.akamai.miniwsa.exception;

public class InvalidRequestShapeException extends RuntimeException {

    public InvalidRequestShapeException(String message) {
        super(message);
    }

    public InvalidRequestShapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
