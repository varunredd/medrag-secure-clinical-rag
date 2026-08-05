package com.medrag.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ClinicalJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Set<String> MEDRAG_ROLES = Set.of("DOCTOR", "NURSE", "CLINIC_ADMIN", "AUDITOR");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .map(role -> role.trim().toUpperCase(Locale.ROOT))
                    .filter(MEDRAG_ROLES::contains)
                    .forEach(roles::add);
        }
        var authorities = roles.stream()
                .sorted()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        String principalName = Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                .orElse(jwt.getSubject());
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }
}
