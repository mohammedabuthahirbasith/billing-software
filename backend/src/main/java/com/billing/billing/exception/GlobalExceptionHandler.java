package com.billing.billing.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

// Single place where every exception leaving a controller becomes an HTTP response, so that
// (a) nothing reaches the client as an unlogged mystery and (b) nothing internal leaks into the
// body. Without it, Boot's default error handling ran with `spring.web.error.include-message=always`,
// which meant an unexpected exception's raw message — a JDBC/constraint string, for instance —
// was echoed to API clients, while handled 4xx failures produced no server-side record at all.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNEXPECTED_MESSAGE =
            "Unexpected server error. Please retry; if it persists, contact support.";

    // Covers ResponseStatusException (every deliberate business failure in the services) plus the
    // framework's own ErrorResponse exceptions (404 for an unmapped path, 405, unsupported media type).
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleErrorResponse(ErrorResponseException ex,
                                                                 HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex instanceof ResponseStatusException rse && rse.getReason() != null
                ? rse.getReason()
                : ex.getBody().getDetail();
        logByStatus(status, message, request, ex);
        return build(status, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.debug("Validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                fieldErrors));
    }

    // Constraint violations on @PathVariable/@RequestParam (not on a @RequestBody DTO) arrive here.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerValidation(HandlerMethodValidationException ex,
                                                                    HttpServletRequest request) {
        log.debug("Request parameter validation failed for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        // The parser's own message can quote the offending payload back at the caller, so it is logged
        // rather than returned.
        log.debug("Unreadable request body for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    // A lost optimistic-lock race is an expected outcome under concurrent stock edits, not a bug:
    // 409 tells the client to retry. Services map the races they flush explicitly; this catches the
    // ones that surface on commit, after the service method has already returned.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                                  HttpServletRequest request) {
        log.warn("Optimistic locking failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, "Record changed concurrently, please retry", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        // Constraint names and SQL fragments belong in the log, never in the response body.
        log.error("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, "Request conflicts with existing data", request);
    }

    // Method security (@PreAuthorize) throws this from inside the controller invocation. Rethrowing
    // hands it back to Spring Security's ExceptionTranslationFilter, which turns it into the correct
    // 403/401 — handling it here would misreport every authorization failure as a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_MESSAGE, request);
    }

    private void logByStatus(HttpStatusCode status, String message, HttpServletRequest request, Exception ex) {
        // 4xx is the caller's problem and is expected traffic (bad barcode, insufficient stock) — debug.
        // 5xx is ours: log the full stack trace even though the client only gets the reason text.
        if (status.is5xxServerError()) {
            log.error("{} {} failed with {}: {}", request.getMethod(), request.getRequestURI(), status, message, ex);
        } else {
            log.debug("{} {} rejected with {}: {}", request.getMethod(), request.getRequestURI(), status, message);
        }
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatusCode status, String message, HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reasonPhrase = resolved != null ? resolved.getReasonPhrase() : "Error";
        String body = message != null ? message : reasonPhrase;
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), reasonPhrase, body, request.getRequestURI()));
    }
}
