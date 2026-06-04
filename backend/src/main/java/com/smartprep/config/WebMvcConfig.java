package com.smartprep.config;

import com.smartprep.security.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the rate limiting interceptor for AI-intensive endpoints only.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/reading/generate",
                        "/api/v1/listening/generate",
                        "/api/v1/writing/grade",
                        "/api/v1/listening/ai-analyze/**",
                        "/api/v1/listening/vocabulary/**",
                        "/api/v1/vocab/ai-suggest"
                );
    }
}
