package com.medrag.api.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemKeys {
    private PemKeys() {}

    public static RSAPrivateKey readPrivate(String path) {
        try {
            String pem = Files.readString(Path.of(path)).replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s", "");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load internal JWT private key", e);
        }
    }

    public static RSAPublicKey readPublic(String path) {
        try {
            String pem = Files.readString(Path.of(path)).replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load internal JWT public key", e);
        }
    }
}
