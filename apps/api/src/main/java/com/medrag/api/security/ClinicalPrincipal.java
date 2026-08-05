package com.medrag.api.security;

import java.util.Set;

public record ClinicalPrincipal(String actorId, String tenantId, Set<String> roles) {
    public boolean hasRole(String role) { return roles.contains(role); }
}
