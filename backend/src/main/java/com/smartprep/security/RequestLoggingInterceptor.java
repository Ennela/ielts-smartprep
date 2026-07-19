package com.smartprep.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.regex.Pattern;

/**
 * Global HTTP request/response logging interceptor.
 * <p>
 * Logs method, URI, status, duration, and user context for every API request.
 * Query values and request/response bodies are deliberately not logged because
 * they can contain credentials or one-time tokens.
 */
@Component
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "requestStartTime";

    /** Pattern to detect sensitive JSON fields: "password":"value" → "password":"***" */
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(\"(?:password|newPassword|currentPassword|token|refreshToken|secret|apiKey)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        if (log.isDebugEnabled()) {
            log.debug("→ {} {} ip={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    getClientIp(request));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (status >= 500) {
            log.error("← {} {} status={} duration={}ms exception={}",
                    method, uri, status, duration,
                    ex != null ? ex.getClass().getSimpleName() : "none");
        } else if (status >= 400) {
            log.warn("← {} {} status={} duration={}ms",
                    method, uri, status, duration);
        } else {
            log.info("← {} {} status={} duration={}ms",
                    method, uri, status, duration);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Mask sensitive fields in a JSON body string.
     */
    public static String maskJsonBody(String body) {
        if (body == null || body.isBlank()) return body;
        return SENSITIVE_JSON.matcher(body).replaceAll("$1\"***\"");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
