package com.medrag.api.config;

import com.medrag.api.security.ApiRateLimitFilter;
import com.medrag.api.security.AuthenticatedMdcFilter;
import com.medrag.api.security.ClinicalJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClinicalJwtAuthenticationConverter converter,
            ApiRateLimitFilter apiRateLimitFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/.well-known/internal-jwks.json").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(apiRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new AuthenticatedMdcFilter(), ApiRateLimitFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()))
                .build();
    }

    @Bean
    ApiRateLimitFilter apiRateLimitFilter(
            StringRedisTemplate redis,
            @Value("${medrag.rate-limits.general-per-minute:120}") int general,
            @Value("${medrag.rate-limits.queries-per-minute:30}") int queries,
            @Value("${medrag.rate-limits.uploads-per-minute:20}") int uploads
    ) {
        return new ApiRateLimitFilter(redis, general, queries, uploads);
    }

    @Bean
    JwtDecoder jwtDecoder(org.springframework.core.env.Environment env, MedRagProperties properties) {
        String issuer = env.getRequiredProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwkSet = env.getRequiredProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSet).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(properties.security().expectedAudience()));
        OAuth2TokenValidator<Jwt> tenantValidator = new JwtClaimValidator<String>("tenant_id",
                tenant -> tenant != null && tenant.matches("[A-Za-z0-9_-]{2,120}"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator, audienceValidator, tenantValidator, breakGlassValidator()
        ));
        return decoder;
    }
    private static OAuth2TokenValidator<Jwt> breakGlassValidator() {
        return token -> {
            Object raw = token.getClaim("break_glass");
            boolean active = Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
            if (!active) {
                return OAuth2TokenValidatorResult.success();
            }
            String reasonId = token.getClaimAsString("break_glass_reason_id");
            boolean validReason = reasonId != null && reasonId.matches("[A-Za-z0-9._-]{3,120}");
            boolean validLifetime = token.getIssuedAt() != null
                    && token.getExpiresAt() != null
                    && !token.getExpiresAt().isBefore(token.getIssuedAt())
                    && Duration.between(token.getIssuedAt(), token.getExpiresAt()).compareTo(Duration.ofMinutes(15)) <= 0;
            if (validReason && validLifetime) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "Emergency-access token does not satisfy reason and lifetime policy",
                    null
            ));
        };
    }

}
