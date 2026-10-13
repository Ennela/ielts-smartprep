package com.smartprep.exception;

import com.smartprep.dto.response.ApiResponse;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(WordCountTooLowException.class)
    public ResponseEntity<ApiResponse<Void>> handleWordCount(WordCountTooLowException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage(), "WORD_COUNT_TOO_LOW"));
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiError(AiServiceException ex) {
        log.error("AI service error", ex);
        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("AI service is unavailable", "AI_SERVICE_ERROR"));
    }

    @ExceptionHandler(InvalidAiResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAiResponse(InvalidAiResponseException ex) {
        log.error("Invalid AI response", ex);
        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("AI returned an invalid response", "INVALID_AI_RESPONSE"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        return ResponseEntity.status(423) // 423 Locked
                .body(ApiResponse.error(ex.getMessage(), "ACCOUNT_LOCKED"));
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountSuspended(AccountSuspendedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), "ACCOUNT_SUSPENDED"));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_TOKEN"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Service unavailable", ex);
        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Service is temporarily unavailable", "SERVICE_UNAVAILABLE"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage(), "RATE_LIMIT_EXCEEDED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation ({})", ex.getMostSpecificCause().getClass().getSimpleName());
        String message = "Cannot complete operation due to a data constraint.";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message, "DATA_INTEGRITY_VIOLATION"));
    }

    /**
     * Safety net for authorization failures.
     * <p>
     * Ownership checks should raise {@link ResourceNotFoundException} so a caller cannot
     * tell "exists but is not yours" from "does not exist". This handler exists so that a
     * {@code SecurityException} raised anywhere still returns 403 rather than falling into
     * the catch-all below, which answered 500 and paged Sentry for what is a routine client
     * error.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurity(SecurityException ex) {
        log.warn("Authorization failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    /**
     * The catch-all. Spring MVC's own request errors -- an unknown path, a method the path
     * does not take, a missing parameter, an unsupported content type -- also arrive here,
     * because this handler is broader than Spring's defaults. They carry their own 4xx
     * status (they implement {@link ErrorResponse}), so they keep it instead of becoming a
     * 500 that pages Sentry for a client mistake. A 405 keeps its Allow header.
     */
    /**
     * A role check made by {@code @PreAuthorize} inside a controller (the URL rules are
     * answered by SecurityConfig's access-denied handler instead). Without this it reached
     * the catch-all below as a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        if (ex instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            log.debug("Client error {}: {}", status.value(), ex.getMessage());
            // NoResourceFoundException's detail says "No static resource ...", which reads as
            // a file lookup; for an API path it is just "not found".
            String message = ex instanceof NoResourceFoundException || error.getBody().getDetail() == null
                    ? status.getReasonPhrase()
                    : error.getBody().getDetail();
            // BAD_REQUEST is the code of handleBadRequest, whose messages are written for the
            // person using the app, and the frontend shows them. Spring's own 400s name a
            // parameter or a request part -- a client bug, not something they can fix -- so
            // they get a code of their own that the frontend does not show.
            String code = status == HttpStatus.BAD_REQUEST ? "INVALID_REQUEST" : status.name();
            return ResponseEntity.status(status)
                    .headers(error.getHeaders())
                    .body(ApiResponse.error(message, code));
        }
        log.error("Unhandled exception", ex);
        Sentry.captureException(ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", "INTERNAL_SERVER_ERROR"));
    }
}
