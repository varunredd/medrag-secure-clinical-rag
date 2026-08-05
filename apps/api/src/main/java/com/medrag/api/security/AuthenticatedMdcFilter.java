package com.medrag.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class AuthenticatedMdcFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            MDC.put("tenantId", authentication.getToken().getClaimAsString("tenant_id"));
            MDC.put("actorId", authentication.getToken().getSubject());
            Object rawBreakGlass = authentication.getToken().getClaim("break_glass");
            boolean breakGlass = Boolean.TRUE.equals(rawBreakGlass)
                    || "true".equalsIgnoreCase(String.valueOf(rawBreakGlass));
            if (breakGlass) {
                MDC.put("breakGlass", "true");
                MDC.put(
                        "breakGlassReasonHash",
                        sha256(authentication.getToken().getClaimAsString("break_glass_reason_id"))
                );
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            MDC.remove("actorId");
            MDC.remove("breakGlass");
            MDC.remove("breakGlassReasonHash");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
