package com.smartprep.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
