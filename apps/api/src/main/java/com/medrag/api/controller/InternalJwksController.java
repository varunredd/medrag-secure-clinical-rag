package com.medrag.api.controller;

import com.medrag.api.config.MedRagProperties;
import com.medrag.api.security.PemKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
public class InternalJwksController {
    private final Map<String, Object> jwks;

    public InternalJwksController(MedRagProperties properties) {
        RSAKey key = new RSAKey.Builder(PemKeys.readPublic(properties.security().publicKeyPath()))
                .keyID(properties.security().internalKeyId())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        this.jwks = Map.of("keys", List.of(key.toJSONObject()));
    }

    @GetMapping("/.well-known/internal-jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(jwks);
    }
}
