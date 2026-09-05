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
                        // Generates four listening parts in one call, so it is the most
                        // expensive request in the application. It was not metered at all.
                        "/api/v1/listening/generate-mock",
                        "/api/v1/listening/*/generate-audio",
                        "/api/v1/writing/grade",
                        "/api/v1/listening/ai-analyze/**",
                        "/api/v1/listening/vocabulary/**",
                        "/api/v1/vocab/ai-suggest"
                )
                .order(1);

        // Auth rate limiting on public auth endpoints (order 2 = after logging)
        //
        // /auth/refresh is deliberately absent. It is keyed by IP like the rest of this
        // list, and every user behind one NAT — an office, a school, a university campus,
        // exactly this product's audience — shares that key. A browser refreshes on 401,
        // so a burst is normal rather than suspicious, and the failure mode of getting it
        // wrong is logging out legitimate users in groups. The abuse it would prevent is
        // already bounded: a refresh needs a valid token whose JTI is rotated and revoked
        // on use, so replaying one is worth nothing.
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password",
                        // Consumes a reset token. Unmetered, it was the one public endpoint
                        // where guessing costs an attacker nothing.
                        "/api/v1/auth/reset-password"
                )
                .order(2);
    }
}
