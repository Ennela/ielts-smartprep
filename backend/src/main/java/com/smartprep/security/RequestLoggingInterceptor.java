package com.smartprep.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Global HTTP request/response logging interceptor.
 * <p>
 * Logs method, URI, status, duration, and user context for every API request.
 * Sensitive fields (password, token, secret, authorization) are redacted from
 * query parameters and logged bodies.
 */
@Component
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "requestStartTime";

    /** Fields whose values are masked in logs */
    private static final Set<String> SENSITIVE_PARAMS = Set.of(
            "password", "newpassword", "currentpassword",
            "token", "refreshtoken", "accesstoken",
            "secret", "authorization", "apikey",
            "mail_password", "jwt_secret"
    );

    /** Pattern to detect sensitive JSON fields: "password":"value" → "password":"***" */
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(\"(?:password|newPassword|currentPassword|token|refreshToken|secret|apiKey)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        if (log.isDebugEnabled()) {
            log.debug("→ {} {} query={} ip={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    maskQueryString(request.getQueryString()),
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
                    ex != null ? ex.getClass().getSimpleName() + ": " + ex.getMessage() : "none");
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
     * Mask sensitive query parameters: ?token=abc&name=John → ?token=***&name=John
     */
    private String maskQueryString(String queryString) {
        if (queryString == null) return null;

        StringBuilder masked = new StringBuilder();
        for (String param : queryString.split("&")) {
            if (masked.length() > 0) masked.append("&");
            int eq = param.indexOf('=');
            if (eq > 0) {
                String key = param.substring(0, eq);
                if (SENSITIVE_PARAMS.contains(key.toLowerCase())) {
                    masked.append(key).append("=***");
                } else {
                    masked.append(param);
                }
            } else {
                masked.append(param);
            }
        }
        return masked.toString();
    }

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
