package com.smartprep.config;

import com.smartprep.security.RateLimitInterceptor;
import com.smartprep.security.RequestLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC interceptors:
 * <ol>
 *   <li>Request logging — all API paths (runs first)</li>
 *   <li>Rate limiting — AI-intensive endpoints only</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final RequestLoggingInterceptor requestLoggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Global request/response logging (order 0 = first)
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**")
                .order(0);

        // Rate limiting on AI endpoints only (order 1 = after logging)
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/reading/generate",
                        "/api/v1/listening/generate",
                        "/api/v1/writing/grade",
                        "/api/v1/listening/ai-analyze/**",
                        "/api/v1/listening/vocabulary/**",
                        "/api/v1/vocab/ai-suggest"
                )
                .order(1);
    }
}
