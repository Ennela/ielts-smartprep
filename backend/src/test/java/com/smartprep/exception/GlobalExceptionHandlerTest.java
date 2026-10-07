package com.smartprep.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException should return 404")
    void handleNotFound_returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("User not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found", response.getBody().getMessage());
    }

    @Test
    @DisplayName("ServiceUnavailableException should return 503")
    void handleServiceUnavailable_returns503() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleServiceUnavailable(new ServiceUnavailableException("AI service down"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertFalse(response.getBody().getMessage().contains("AI service down"));
    }

    @Test
    @DisplayName("RateLimitExceededException should return 429")
    void handleRateLimit_returns429() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleRateLimit(new RateLimitExceededException("Too many requests"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("Too many requests"));
    }

    @Test
    @DisplayName("AiServiceException should return 503")
    void handleAiError_returns503() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAiError(new AiServiceException("Gemini timeout"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().getMessage().contains("Gemini timeout"));
    }

    @Test
    @DisplayName("IllegalArgumentException should return 400")
    void handleBadRequest_returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new IllegalArgumentException("Invalid input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid input", response.getBody().getMessage());
        assertEquals("BAD_REQUEST", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("DataIntegrityViolationException should not expose database details")
    void handleDataIntegrity_doesNotExposeDatabaseDetails() {
        String internalDetail = "internal database constraint detail";
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("write failed", new RuntimeException(internalDetail)));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().getMessage().contains(internalDetail));
        assertEquals("Cannot complete operation due to a data constraint.", response.getBody().getMessage());
    }

    @Test
    @DisplayName("an unknown path is a 404, not a 500")
    void unknownPath_returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new NoResourceFoundException(HttpMethod.GET, "api/v1/nope"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().getErrorCode());
        assertFalse(response.getBody().getMessage().contains("static resource"));
    }

    @Test
    @DisplayName("a wrong HTTP method is a 405 that says which methods are allowed")
    void wrongMethod_returns405WithAllow() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new HttpRequestMethodNotSupportedException("GET", List.of("POST", "PUT")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getErrorCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.ALLOW).contains("POST"));
    }

    @Test
    @DisplayName("a missing query parameter is a 400 naming the parameter")
    void missingParameter_returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new MissingServletRequestParameterException("skill", "String"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("skill"));
        // Not BAD_REQUEST: the frontend shows BAD_REQUEST messages to the user.
        assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("anything else is still a 500 that hides its message")
    void otherErrors_stay500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new IllegalStateException("internal detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().getMessage());
    }
}
