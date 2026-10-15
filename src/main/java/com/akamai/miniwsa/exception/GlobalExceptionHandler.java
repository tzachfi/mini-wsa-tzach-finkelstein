package com.akamai.miniwsa.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EventValidationException.class)
    ProblemDetail handleValidation(EventValidationException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setProperty("errors", ex.getFieldErrors());
        return problem;
    }

    @ExceptionHandler(InvalidRequestShapeException.class)
    ProblemDetail handleInvalidShape(InvalidRequestShapeException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Malformed JSON or invalid field types");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve
                && cve.getConstraintName() != null
                && cve.getConstraintName().toLowerCase().contains("event_id")) {
            var problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "An event with this eventId already exists");
            problem.setTitle("Conflict");
            return problem;
        }
        log.warn("Data integrity violation", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Event violates a storage constraint");
        problem.setTitle("Bad Request");
        return problem;
    }
}
