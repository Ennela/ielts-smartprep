package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.AiServiceException;
import com.smartprep.exception.InvalidAiResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GeminiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.base-url}")
    private String baseUrl;

    @Value("${app.gemini.max-retries:3}")
    private int maxRetries;

    public GeminiClient(@Qualifier("geminiRestTemplate") RestTemplate restTemplate,
                        ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a prompt to Gemini API and return the text response.
     * Implements retry with exponential backoff.
     */
    public String generate(String systemPrompt, String userPrompt) {
        String url = baseUrl + ":generateContent?key=" + apiKey;

        Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, entity, String.class);

                return extractTextFromResponse(response.getBody());

            } catch (ResourceAccessException ex) {
                log.warn("Gemini API timeout (attempt {}/{}): {}", attempt, maxRetries, ex.getMessage());
                if (attempt == maxRetries) {
                    throw new AiServiceException("Gemini API timeout after " + maxRetries + " attempts", ex);
                }
                sleepWithBackoff(attempt);

            } catch (AiServiceException | InvalidAiResponseException ex) {
                throw ex;

            } catch (Exception ex) {
                log.warn("Gemini API error (attempt {}/{}): {}", attempt, maxRetries, ex.getMessage());
                if (attempt == maxRetries) {
                    throw new AiServiceException("Gemini API error after " + maxRetries + " attempts", ex);
                }
                sleepWithBackoff(attempt);
            }
        }

        throw new AiServiceException("Gemini API failed after all retries");
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "topP", 0.9,
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
