package com.akamai.miniwsa.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(StatsQueryException.class)
    ProblemDetail handleStatsQuery(StatsQueryException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(TaskRejectedException.class)
    ResponseEntity<ProblemDetail> handleRejected(TaskRejectedException ex) {
        log.warn("Stats and samples query executor at capacity", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Stats and samples queries are at capacity; retry shortly");
        problem.setTitle("Service Unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Required parameter '%s' is missing".formatted(ex.getParameterName()));
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid value for parameter '%s'".formatted(ex.getName()));
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
