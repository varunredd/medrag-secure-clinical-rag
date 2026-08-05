package com.medrag.api.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "export_request")
public class ExportRequest {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 120)
    private String tenantId;

    @Column(name = "requested_by", nullable = false, length = 120)
    private String requestedBy;

    @Column(name = "request_type", nullable = false, length = 40)
    private String requestType;

    @Column(name = "subject_reference_hash", length = 64)
    private String subjectReferenceHash;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExportRequest() {
    }

    public static ExportRequest create(
            String tenantId,
            String requestedBy,
            String requestType,
            String subjectReferenceHash
    ) {
        Instant now = Instant.now();
        ExportRequest request = new ExportRequest();
        request.id = UUID.randomUUID();
        request.tenantId = tenantId;
        request.requestedBy = requestedBy;
        request.requestType = requestType;
        request.subjectReferenceHash = subjectReferenceHash;
        request.status = "REVIEW_REQUIRED";
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getRequestedBy() { return requestedBy; }
    public String getRequestType() { return requestType; }
    public String getSubjectReferenceHash() { return subjectReferenceHash; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
