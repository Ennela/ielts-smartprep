package com.smartprep.service.ai;

import com.smartprep.exception.AiServiceException;
import com.smartprep.exception.InvalidAiResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    private RestTemplate restTemplate;
    private GeminiClient geminiClient;

    private static final String VALID_RESPONSE = """
            {
              "candidates": [{
                "content": {
                  "parts": [{"text": "{\\"quiz\\": \\"data\\"}"}]
                }
              }]
            }
            """;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        io.github.resilience4j.retry.RetryConfig config = io.github.resilience4j.retry.RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(java.time.Duration.ofMillis(10))
                .retryExceptions(ResourceAccessException.class, AiServiceException.class)
                .build();
        io.github.resilience4j.retry.RetryRegistry registry = io.github.resilience4j.retry.RetryRegistry.of(config);
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        geminiClient = new GeminiClient(restTemplate, new ObjectMapper(), registry, redisTemplate);
        ReflectionTestUtils.setField(geminiClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiClient, "baseUrl", "https://api.example.com/model");
        ReflectionTestUtils.setField(geminiClient, "maxRetries", 2);
    }

    @Test
    @DisplayName("should return text from valid Gemini response")
    void generate_validResponse_returnsText() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(VALID_RESPONSE));

        String result = geminiClient.generate("system prompt", "user prompt");

        assertEquals("{\"quiz\": \"data\"}", result);
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("should retry on timeout and succeed on second attempt")
    void generate_timeoutThenSuccess_retries() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"))
                .thenReturn(ResponseEntity.ok(VALID_RESPONSE));

        String result = geminiClient.generate("system", "user");

        assertEquals("{\"quiz\": \"data\"}", result);
        verify(restTemplate, times(2)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("should throw AiServiceException after all retries exhausted")
    void generate_allRetriesFail_throwsException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThrows(AiServiceException.class,
                () -> geminiClient.generate("system", "user"));
        verify(restTemplate, times(2)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("should throw InvalidAiResponseException for empty candidates")
    void generate_emptyCandidates_throwsInvalidResponse() {
        String emptyResponse = "{\"candidates\": []}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(emptyResponse));

        assertThrows(InvalidAiResponseException.class,
                () -> geminiClient.generate("system", "user"));
    }
}
