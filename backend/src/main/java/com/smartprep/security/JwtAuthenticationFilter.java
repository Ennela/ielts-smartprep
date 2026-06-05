package com.smartprep.security;

import com.smartprep.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // Only accept access tokens — reject refresh tokens used as access
            String tokenType = jwtTokenProvider.getTokenTypeFromToken(token);
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Check if this token's JTI has been blacklisted (e.g., after logout)
            String jti = jwtTokenProvider.getJtiFromToken(token);
            if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:jti:" + jti))) {
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            // Enrich MDC with userId for structured logging
            TraceIdFilter.setUserId(String.valueOf(userId));

            userRepository.findById(userId).ifPresent(user -> {
                var authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "STUDENT"))
                );
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
