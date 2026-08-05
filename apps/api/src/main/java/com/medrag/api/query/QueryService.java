package com.medrag.api.query;

import com.medrag.api.ai.AiClient;
import com.medrag.api.audit.AuditService;
import com.medrag.api.document.ClinicalDocumentRepository;
import com.medrag.api.document.Hashing;
import com.medrag.api.document.DocumentStatus;
import com.medrag.api.metering.UsageMeterService;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.tenant.TenantSettingService;
import com.medrag.api.web.BadRequestException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QueryService {
    private final ClinicalDocumentRepository documents;
    private final AiClient ai;
    private final AuditService audit;
    private final UsageMeterService meter;
    private final TenantSettingService tenantSettings;

    public QueryService(
            ClinicalDocumentRepository documents,
            AiClient ai,
            AuditService audit,
            UsageMeterService meter,
            TenantSettingService tenantSettings
    ) {
        this.documents = documents;
        this.ai = ai;
        this.audit = audit;
        this.meter = meter;
        this.tenantSettings = tenantSettings;
    }

    public AiClient.QueryResponse query(
            ClinicalPrincipal principal,
            String question,
            List<UUID> documentIds,
            int topK
    ) {
        String normalizedQuestion = question.strip().replaceAll("\\s+", " ");
        List<UUID> scopedIds = List.copyOf(new LinkedHashSet<>(documentIds));
        long readyCount = documents.countByIdInAndTenantIdAndDeletedAtIsNullAndStatus(
                scopedIds,
                principal.tenantId(),
                DocumentStatus.READY
        );
        if (readyCount != scopedIds.size()) {
            throw new BadRequestException("INVALID_OR_UNREADY_DOCUMENT_SCOPE");
        }

        try {
            TenantSettingService.GenerationPolicy generation =
                    tenantSettings.generationPolicy(principal.tenantId());
            AiClient.QueryResponse response = ai.query(
                    principal,
                    new AiClient.QueryRequest(
                            principal.tenantId(),
                            normalizedQuestion,
                            scopedIds,
                            Math.max(1, Math.min(topK, 20)),
                            generation.mode(),
                            generation.endpointRef(),
                            generation.secretRef(),
                            generation.model()
                    )
            );
            audit.record(
                    principal,
                    "CLINICAL_QUERY",
                    "DOCUMENT_SET",
                    null,
                    "SUCCESS",
                    Map.of(
                            "documentCount", scopedIds.size(),
                            "questionSha256", Hashing.sha256(normalizedQuestion.getBytes(StandardCharsets.UTF_8)),
                            "citationCount", response.citations().size(),
                            "embeddingModel", response.embeddingModel(),
                            "generationModel", response.generationModel()
                    )
            );
            meter.emit(principal, "CLINICAL_QUERY", 1, null);
            return response;
        } catch (RuntimeException error) {
            audit.recordFailure(
                    principal,
                    "CLINICAL_QUERY",
                    "DOCUMENT_SET",
                    null,
                    Map.of(
                            "code", "AI_QUERY_FAILED",
                            "documentCount", scopedIds.size(),
                            "questionSha256", Hashing.sha256(normalizedQuestion.getBytes(StandardCharsets.UTF_8))
                    )
            );
            throw error;
        }
    }
}
