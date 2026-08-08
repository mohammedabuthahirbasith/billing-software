package com.billing.billing.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

// Mirrors the field names Boot's default error attributes already produced (status/error/message/path)
// so the existing frontend contract — read `message`, fall back to raw text — keeps working unchanged,
// with `fieldErrors` added for bean-validation failures.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null);
    }
}
