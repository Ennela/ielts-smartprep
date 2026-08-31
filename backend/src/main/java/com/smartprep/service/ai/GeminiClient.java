package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.AiServiceException;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GeminiClient {

    @FunctionalInterface
    public interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Retry retry;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.base-url}")
    private String baseUrl;

    @Value("${app.gemini.max-retries:3}")
    private int maxRetries;

    public GeminiClient(@Qualifier("geminiRestTemplate") RestTemplate restTemplate,
                         ObjectMapper objectMapper,
                         RetryRegistry retryRegistry,
                         StringRedisTemplate redisTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.retry = retryRegistry.retry("gemini");
        this.redisTemplate = redisTemplate;
    }

    /** Sampling temperature for content generation, where variety is desirable. */
    private static final double CREATIVE_TEMPERATURE = 0.7;

    /** Sampling temperature for grading, where the same input must always produce the same band. */
    private static final double GRADING_TEMPERATURE = 0.0;

    /**
     * Send a prompt to Gemini API, parse and validate the response within the retry boundary.
     * Retries automatically using Resilience4j when timeouts or validation errors occur.
     */
    @CircuitBreaker(name = "gemini")
    public <T> T generateAndParse(String systemPrompt, String userPrompt, CheckedFunction<String, T> parser) {
        return executeAndParse(systemPrompt, userPrompt, parser, CREATIVE_TEMPERATURE);
    }

    /**
     * Same as {@link #generateAndParse} but sampled deterministically.
     * <p>
     * Grading must be reproducible: at temperature 0.7 the same essay could come back with a
     * different band on every submission, which is indefensible for a scoring product.
     * Content generation keeps the higher temperature, where variety is the point.
     */
    @CircuitBreaker(name = "gemini")
    public <T> T gradeAndParse(String systemPrompt, String userPrompt, CheckedFunction<String, T> parser) {
        return executeAndParse(systemPrompt, userPrompt, parser, GRADING_TEMPERATURE);
    }

    private <T> T executeAndParse(String systemPrompt, String userPrompt,
                                  CheckedFunction<String, T> parser, double temperature) {
        return retry.executeSupplier(() -> {
            String rawResponse = generateInternal(systemPrompt, userPrompt, temperature);
            try {
                return parser.apply(rawResponse);
            } catch (Exception ex) {
                log.warn("AI response validation/parsing failed ({})", ex.getClass().getSimpleName());
                if (ex instanceof InvalidAiResponseException) {
                    throw (InvalidAiResponseException) ex;
                }
                throw new InvalidAiResponseException("AI response validation/parsing failed: " + ex.getMessage(), ex);
            }
        });
    }

    /**
     * Send a prompt to Gemini API and return the text response.
     * Protected by Resilience4j Retry & Circuit Breaker.
     */
    @CircuitBreaker(name = "gemini", fallbackMethod = "fallbackGenerate")
    public String generate(String systemPrompt, String userPrompt) {
        return retry.executeSupplier(() -> generateInternal(systemPrompt, userPrompt, CREATIVE_TEMPERATURE));
    }

    private String generateInternal(String systemPrompt, String userPrompt, double temperature) {
        if (!StringUtils.hasText(apiKey)) {
            throw new ServiceUnavailableException("Gemini API key is not configured");
        }
        String url = baseUrl + ":generateContent";
        Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, temperature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            String responseBody = response.getBody();
            logAndTrackUsage(responseBody);
            return extractTextFromResponse(responseBody);
        } catch (ResourceAccessException ex) {
            log.warn("Gemini API timeout/network access error");
            throw new AiServiceException("Gemini API timeout or connection failure", ex);
        } catch (Exception ex) {
            log.warn("Gemini API call failed ({})", ex.getClass().getSimpleName());
            if (ex instanceof AiServiceException || ex instanceof InvalidAiResponseException) {
                throw ex;
            }
            throw new AiServiceException("Gemini API request failed", ex);
        }
    }

    private void logAndTrackUsage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode usage = root.path("usageMetadata");
            int promptTokens = usage.path("promptTokenCount").asInt(0);
            int candidatesTokens = usage.path("candidatesTokenCount").asInt(0);
            int totalTokens = usage.path("totalTokenCount").asInt(0);

            // Log using SLF4J
            log.info("Gemini API Call: Prompt Tokens = {}, Candidates Tokens = {}, Total Tokens = {}",
                    promptTokens, candidatesTokens, totalTokens);

            // Track globally in Redis
            if (redisTemplate != null) {
                redisTemplate.opsForValue().increment("metrics:gemini:calls");
                if (promptTokens > 0) {
                    redisTemplate.opsForValue().increment("metrics:gemini:prompt-tokens", promptTokens);
                }
                if (candidatesTokens > 0) {
                    redisTemplate.opsForValue().increment("metrics:gemini:candidates-tokens", candidatesTokens);
                }
                if (totalTokens > 0) {
                    redisTemplate.opsForValue().increment("metrics:gemini:total-tokens", totalTokens);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to log/track Gemini token usage: {}", e.getMessage());
        }
    }

    /**
     * Fallback method invoked when circuit breaker is OPEN or call fails.
     */
    private String fallbackGenerate(String systemPrompt, String userPrompt, Throwable throwable) {
        log.error("Circuit breaker fallback for Gemini API ({})", throwable.getClass().getSimpleName());
        throw new ServiceUnavailableException(
                "AI service is temporarily unavailable. Please try again in a few moments.");
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, double temperature) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "topP", temperature == GRADING_TEMPERATURE ? 1.0 : 0.9,
                        "responseMimeType", "application/json"
                )
        );
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                throw new InvalidAiResponseException("No candidates in Gemini response");
            }
            JsonNode textNode = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");

            if (textNode.isMissingNode()) {
                throw new InvalidAiResponseException("No text in Gemini response");
            }
            return textNode.asText();
        } catch (InvalidAiResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidAiResponseException("Failed to parse Gemini response", ex);
        }
    }

    private void sleepWithBackoff(int attempt) {
        try {
            long sleepMs = (long) Math.pow(2, attempt - 1) * 1000;
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
