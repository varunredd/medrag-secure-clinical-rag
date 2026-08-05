package com.medrag.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public final class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int generalRequestsPerMinute;
    private final int queryRequestsPerMinute;
    private final int uploadRequestsPerMinute;

    public ApiRateLimitFilter(
            StringRedisTemplate redis,
            int generalRequestsPerMinute,
            int queryRequestsPerMinute,
            int uploadRequestsPerMinute
    ) {
        this.redis = redis;
        this.generalRequestsPerMinute = positive(generalRequestsPerMinute, "general rate limit");
        this.queryRequestsPerMinute = positive(queryRequestsPerMinute, "query rate limit");
        this.uploadRequestsPerMinute = positive(uploadRequestsPerMinute, "upload rate limit");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = authentication.getToken().getClaimAsString("tenant_id");
        String subject = authentication.getToken().getSubject();
        String className = requestClass(request);
        int limit = limitFor(className);
        long bucket = Instant.now().getEpochSecond() / 60;
        String key = "medrag:api-rate:" + digest(tenantId + "\u0000" + subject)
                + ":" + className + ":" + bucket;

        try {
            Long count = redis.execute(INCREMENT, List.of(key), "60");
            if (count == null) {
                writeProblem(response, 503, "RATE_LIMIT_DEPENDENCY_UNAVAILABLE", "Request protection is unavailable");
                return;
            }
            response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
            response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, limit - count)));
            if (count > limit) {
                response.setHeader("Retry-After", "60");
                writeProblem(response, 429, "RATE_LIMITED", "Too many requests");
                return;
            }
        } catch (DataAccessException error) {
            writeProblem(response, 503, "RATE_LIMIT_DEPENDENCY_UNAVAILABLE", "Request protection is unavailable");
            return;
        }

        chain.doFilter(request, response);
    }

    private String requestClass(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/queries")) {
            return "query";
        }
        if (path.equals("/api/v1/documents") && "POST".equalsIgnoreCase(request.getMethod())) {
            return "upload";
        }
        return "general";
    }

    private int limitFor(String requestClass) {
        return switch (requestClass) {
            case "query" -> queryRequestsPerMinute;
            case "upload" -> uploadRequestsPerMinute;
            default -> generalRequestsPerMinute;
        };
    }

    private void writeProblem(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId = MDC.get("requestId") == null ? "unknown" : MDC.get("requestId");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Request rejected\","
                + "\"status\":" + status + ",\"detail\":\"" + detail + "\","
                + "\"code\":\"" + code + "\",\"requestId\":\"" + requestId + "\"}");
    }

    private static int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
