package com.medrag.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "medrag")
public record MedRagProperties(
        Security security,
        Ai ai,
        Storage storage,
        Uploads uploads,
        Clamav clamav,
        Cors cors
) {
    public record Security(
            String expectedAudience,
            String internalIssuer,
            String internalAudience,
            Duration internalTokenTtl,
            String internalKeyId,
            String privateKeyPath,
            String publicKeyPath
    ) {
    }

    public record Ai(
            URI baseUrl,
            Duration connectTimeout,
            Duration responseTimeout
    ) {
    }

    public record Storage(
            String endpoint,
            String accessKey,
            String secretKey,
            String region,
            String documentBucket,
            String sseAlgorithm,
            String kmsKeyId,
            boolean useDefaultCredentials,
            boolean pathStyleAccessEnabled
    ) {
    }

    public record Uploads(long maxBytes, Set<String> allowedMimeTypes) {
    }

    public record Clamav(String host, int port, Duration timeout) {
    }

    public record Cors(Set<String> allowedOrigins) {
    }
}
