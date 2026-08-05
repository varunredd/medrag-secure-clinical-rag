package com.medrag.api.security;

import com.medrag.api.config.MedRagProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class InternalTokenService {
    private static final Duration MAX_INTERNAL_TOKEN_TTL = Duration.ofSeconds(90);

    private final MedRagProperties properties;
    private final Clock clock;
    private final RSASSASigner signer;

    @Autowired
    public InternalTokenService(MedRagProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InternalTokenService(MedRagProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        Duration ttl = properties.security().internalTokenTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_INTERNAL_TOKEN_TTL) > 0) {
            throw new IllegalStateException("Internal AI token TTL must be between 1 and 90 seconds");
        }

        RSAPrivateKey privateKey = PemKeys.readPrivate(properties.security().privateKeyPath());
        RSAPublicKey publicKey = PemKeys.readPublic(properties.security().publicKeyPath());
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("Internal JWT private and public keys do not form a matching RSA key pair");
        }
        if (privateKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("Internal JWT RSA key must be at least 2048 bits");
        }
        this.signer = new RSASSASigner(privateKey);
    }

    public String mint(ClinicalPrincipal principal, String scope, String requestId) {
        try {
            Instant now = clock.instant();
            var claims = new JWTClaimsSet.Builder()
                    .issuer(properties.security().internalIssuer())
                    .audience(properties.security().internalAudience())
                    .subject(principal.actorId())
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now.minusSeconds(2)))
                    .expirationTime(Date.from(now.plus(properties.security().internalTokenTtl())))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("tenant_id", principal.tenantId())
                    .claim("roles", List.copyOf(principal.roles()))
                    .claim("scope", scope)
                    .claim("request_id", requestId)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(properties.security().internalKeyId())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims
            );
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to mint internal AI token", error);
        }
    }
}
