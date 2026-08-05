package com.medrag.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalJwtAuthenticationConverterTest {
    @Test
    void mapsOnlyMedragRealmRolesAndNormalizesCase() {
        Jwt jwt = jwt(Map.of(
                "sub", "u1",
                "tenant_id", "clinic-a",
                "realm_access", Map.of("roles", List.of("doctor", "offline_access", "AUDITOR"))
        ));

        var auth = new ClinicalJwtAuthenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_AUDITOR", "ROLE_DOCTOR");
    }

    @Test
    void missingRealmRolesProducesNoClinicalAuthority() {
        var auth = new ClinicalJwtAuthenticationConverter().convert(jwt(Map.of(
                "sub", "u1",
                "tenant_id", "clinic-a"
        )));

        assertThat(auth.getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                claims
        );
    }
}
