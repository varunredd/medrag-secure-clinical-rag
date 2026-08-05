package com.medrag.api.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_document")
public class ClinicalDocument {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 120)
    private String tenantId;

    @Column(name = "safe_filename", nullable = false, length = 255)
    private String safeFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "uploaded_by", nullable = false, length = 120)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 32)
    private String classification;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    protected ClinicalDocument() {
    }

    public static ClinicalDocument create(
            UUID id,
            String tenantId,
            String safeFilename,
            String contentType,
            long sizeBytes,
            String sha256,
            String objectKey,
            String uploadedBy,
            Instant retentionUntil
    ) {
        Instant now = Instant.now();
        ClinicalDocument document = new ClinicalDocument();
        document.id = id;
        document.tenantId = tenantId;
        document.safeFilename = safeFilename;
        document.contentType = contentType;
        document.sizeBytes = sizeBytes;
        document.sha256 = sha256;
        document.objectKey = objectKey;
        document.status = DocumentStatus.QUEUED;
        document.classification = "PHI_RESTRICTED";
        document.legalHold = false;
        document.retentionUntil = retentionUntil;
        document.uploadedBy = uploadedBy;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    public void markProcessing() {
        status = DocumentStatus.PROCESSING;
        failureCode = null;
        updatedAt = Instant.now();
    }

    public void markReady() {
        status = DocumentStatus.READY;
        failureCode = null;
        updatedAt = Instant.now();
    }

    public void markFailed(String code) {
        status = DocumentStatus.FAILED;
        failureCode = code;
        updatedAt = Instant.now();
    }

    public void setLegalHold(boolean legalHold) {
        this.legalHold = legalHold;
        updatedAt = Instant.now();
    }

    public void setClassification(String classification) {
        this.classification = classification;
        updatedAt = Instant.now();
    }

    public void markDeleted() {
        status = DocumentStatus.DELETED;
        deletedAt = Instant.now();
        updatedAt = deletedAt;
    }

    public boolean canRetry() {
        return deletedAt == null && status == DocumentStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSafeFilename() {
        return safeFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getClassification() {
        return classification;
    }

    public boolean isLegalHold() {
        return legalHold;
    }

    public Instant getRetentionUntil() {
        return retentionUntil;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
