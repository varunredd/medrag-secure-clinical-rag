package com.medrag.api.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.stream.Collectors;

public final class CurrentPrincipal {
    private CurrentPrincipal() {}

    public static ClinicalPrincipal require() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            throw new IllegalStateException("Authenticated JWT principal required");
        }
        String tenant = auth.getToken().getClaimAsString("tenant_id");
        Set<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toUnmodifiableSet());
        return new ClinicalPrincipal(auth.getToken().getSubject(), tenant, roles);
    }
}
