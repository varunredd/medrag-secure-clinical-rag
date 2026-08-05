package com.medrag.api.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, UUID> {
    Optional<ClinicalDocument> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, String tenantId);
    Page<ClinicalDocument> findAllByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);
    long countByTenantIdAndDeletedAtIsNull(String tenantId);
    long countByTenantIdAndDeletedAtIsNullAndStatus(String tenantId, DocumentStatus status);
    List<ClinicalDocument> findTop8ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);
    List<ClinicalDocument> findTop100ByDeletedAtIsNullAndLegalHoldFalseAndRetentionUntilBeforeOrderByRetentionUntilAsc(Instant now);
    long countByIdInAndTenantIdAndDeletedAtIsNullAndStatus(
            Collection<UUID> ids,
            String tenantId,
            DocumentStatus status
    );
}
