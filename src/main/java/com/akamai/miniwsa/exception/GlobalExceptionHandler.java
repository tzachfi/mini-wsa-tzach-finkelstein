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
    ProblemDetail handleDuplicate(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation on ingest", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "An event with this eventId already exists");
        problem.setTitle("Conflict");
        return problem;
    }
}
