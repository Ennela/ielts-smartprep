package com.smartprep.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates a unique traceId per request and places it in MDC so every log line
 * emitted during the request is correlated.
 * <p>
 * If the client sends an {@code X-Request-Id} header, it is reused; otherwise a
 * new UUID is generated. The traceId is also echoed back in the response header.
 * <p>
 * Runs before all other filters (including JwtAuthenticationFilter).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String USER_ID_KEY = "userId";
    private static final String REQUEST_HEADER = "X-Request-Id";
    private static final String RESPONSE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Reuse client-provided trace id, or generate one
            String traceId = request.getHeader(REQUEST_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }

            MDC.put(TRACE_ID_KEY, traceId);
            response.setHeader(RESPONSE_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(USER_ID_KEY);
        }
    }

    /**
     * Utility to set the userId in MDC once authentication is resolved.
     * Called from JwtAuthenticationFilter after successful auth.
     */
    public static void setUserId(String userId) {
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
        }
    }
}
