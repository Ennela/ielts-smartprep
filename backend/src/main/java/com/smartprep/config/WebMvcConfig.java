package com.smartprep.config;

import com.smartprep.security.AuthRateLimitInterceptor;
import com.smartprep.security.RateLimitInterceptor;
import com.smartprep.security.RequestLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC interceptors:
 * <ol>
 *   <li>Request logging — all API paths (runs first, order 0)</li>
 *   <li>Rate limiting — AI-intensive endpoints (order 1)</li>
 *   <li>Auth rate limiting — login, register, forgot-password (order 2)</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuthRateLimitInterceptor authRateLimitInterceptor;
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

        // Auth rate limiting on public auth endpoints (order 2 = after logging)
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password"
                )
                .order(2);
    }
}
