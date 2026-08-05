package com.medrag.api.ai;

import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.security.InternalTokenService;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class AiClient {
    private final WebClient client;
    private final InternalTokenService tokens;

    public AiClient(WebClient aiWebClient, InternalTokenService tokens) {
        this.client = aiWebClient;
        this.tokens = tokens;
    }

    public void ingest(ClinicalPrincipal principal, IngestRequest request) {
        call("/internal/v1/ingestions", "ai:ingest", principal, request, Void.class);
    }

    public void purge(ClinicalPrincipal principal, PurgeRequest request) {
        call("/internal/v1/documents/purge", "ai:purge", principal, request, Void.class);
    }

    public QueryResponse query(ClinicalPrincipal principal, QueryRequest request) {
        return Objects.requireNonNull(
                call("/internal/v1/query", "ai:query", principal, request, QueryResponse.class),
                "AI service returned an empty query response"
        );
    }

    private <T> T call(
            String path,
            String scope,
            ClinicalPrincipal principal,
            Object body,
            Class<T> responseType
    ) {
        String currentRequestId = MDC.get("requestId");
        String requestId = currentRequestId == null ? UUID.randomUUID().toString() : currentRequestId;
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + tokens.mint(principal, scope, requestId))
                .header("X-Request-ID", requestId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    public record IngestRequest(
            String tenantId,
            UUID documentId,
            String objectKey,
            String contentType,
            String sha256
    ) {
    }

    public record PurgeRequest(String tenantId, UUID documentId) {
    }

    public record QueryRequest(
            String tenantId,
            String question,
            List<UUID> documentIds,
            int topK,
            String llmMode,
            String llmEndpointRef,
            String llmSecretRef,
            String llmModel
    ) {
    }

    public record Citation(UUID documentId, int page, int chunkOrdinal, String excerpt, double score) {
    }

    public record QueryResponse(
            String answer,
            List<Citation> citations,
            double confidence,
            String embeddingModel,
            String generationModel,
            String disclaimer
    ) {
    }
}
