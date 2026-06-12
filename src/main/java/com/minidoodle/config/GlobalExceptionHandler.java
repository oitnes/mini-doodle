package com.minidoodle.config;

import com.minidoodle.dto.ErrorResponse;
import com.minidoodle.exception.MeetingNotFoundException;
import com.minidoodle.exception.SlotAlreadyBookedException;
import com.minidoodle.exception.SlotModificationForbiddenException;
import com.minidoodle.exception.SlotNotFoundException;
import com.minidoodle.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * ARCHITECTURE DECISION: extends ResponseEntityExceptionHandler so EVERY
 * framework-raised MVC error (malformed JSON, type mismatch on path/query
 * params, missing parameters, unknown paths, unsupported methods, bean
 * validation) funnels through one override and comes out in the same
 * ErrorResponse shape as the domain errors below. Handling exceptions one
 * class at a time would leave each newly discovered framework exception
 * leaking Spring's default error body and silently breaking the documented
 * error contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Single funnel for all exceptions ResponseEntityExceptionHandler knows.
    // The parent passes a sanitized ProblemDetail as `body`; its detail is
    // preferred over ex.getMessage(), which for parser errors leaks Jackson
    // internals and fully-qualified class names to the client.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        String message = body instanceof ProblemDetail detail && detail.getDetail() != null
                ? detail.getDetail()
                : ex.getMessage() != null ? ex.getMessage() : error;
        return new ResponseEntity<>(ErrorResponse.of(statusCode.value(), error, message), headers, statusCode);
    }

    // Overridden only to produce a "field: message" summary instead of the
    // raw BindingResult text; still returns the shared ErrorResponse shape.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(e -> (e instanceof FieldError field ? field.getField() + ": " : "") + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ResponseEntity<>(ErrorResponse.of(400, "Bad Request", message), headers, status);
    }

    @ExceptionHandler({UserNotFoundException.class, SlotNotFoundException.class, MeetingNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(RuntimeException ex) {
        return ErrorResponse.of(404, "Not Found", ex.getMessage());
    }

    @ExceptionHandler({SlotAlreadyBookedException.class, SlotModificationForbiddenException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(RuntimeException ex) {
        return ErrorResponse.of(409, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ErrorResponse.of(409, "Conflict", "Slot was concurrently modified — please retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("no_overlap")) {
            return ErrorResponse.of(409, "Conflict", "Time slot overlaps with an existing slot");
        }
        // The loser of a concurrent booking usually trips the meeting.slot_id
        // UNIQUE backstop (the INSERT flushes before the version-checked
        // UPDATE) — give it the same message as the status-check path.
        if (cause != null && cause.contains("meeting_slot_id")) {
            return ErrorResponse.of(409, "Conflict", "Slot is already booked");
        }
        return ErrorResponse.of(409, "Conflict", "Data integrity constraint violated");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ErrorResponse.of(400, "Bad Request", ex.getMessage());
    }

    // Spring Data throws this for an unknown ?sort= property; without a
    // handler it would surface as a 500 for a plain client typo.
    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadSortProperty(PropertyReferenceException ex) {
        return ErrorResponse.of(400, "Bad Request", ex.getMessage());
    }

    // Last-resort net: keeps even unexpected failures on the documented error
    // shape and logs the stack trace, without leaking internals to the client.
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred");
    }
}
