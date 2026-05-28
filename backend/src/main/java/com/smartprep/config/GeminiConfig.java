package com.smartprep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeminiConfig {

    @Value("${app.gemini.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean("geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return new RestTemplate(factory);
    }
}
