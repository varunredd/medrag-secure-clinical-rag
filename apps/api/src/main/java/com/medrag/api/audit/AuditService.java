package com.medrag.api.audit;

import com.medrag.api.security.ClinicalPrincipal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Joins the caller transaction when present so a success event cannot commit
     * before the business operation it describes.
     */
    @Transactional
    public void record(
            ClinicalPrincipal principal,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            Map<String, Object> metadata
    ) {
        persist(principal, action, resourceType, resourceId, outcome, metadata);
    }

    /**
     * Persists failure evidence independently after a business operation throws.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            ClinicalPrincipal principal,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata
    ) {
        persist(principal, action, resourceType, resourceId, "FAILURE", metadata);
    }

    private void persist(
            ClinicalPrincipal principal,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            Map<String, Object> metadata
    ) {
        repository.save(AuditEvent.of(
                principal.tenantId(),
                principal.actorId(),
                principal.roles().stream().sorted().collect(Collectors.joining(",")),
                action,
                resourceType,
                resourceId,
                outcome,
                MDC.get("requestId") == null ? "unknown" : MDC.get("requestId"),
                serialize(enrich(metadata))
        ));
    }


    private Map<String, Object> enrich(Map<String, Object> metadata) {
        Map<String, Object> enriched = new LinkedHashMap<>(metadata);
        if ("true".equals(MDC.get("breakGlass"))) {
            enriched.put("breakGlass", true);
            enriched.put("breakGlassReasonSha256", MDC.get("breakGlassReasonHash"));
        }
        return enriched;
    }

    private String serialize(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException error) {
            throw new IllegalStateException("Unable to serialize audit metadata", error);
        }
    }
}
