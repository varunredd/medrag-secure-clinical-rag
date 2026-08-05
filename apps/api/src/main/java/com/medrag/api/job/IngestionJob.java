package com.medrag.api.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "ingestion_job")
public class IngestionJob {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 120)
    private String tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IngestionJob() {
    }

    public static IngestionJob pending(String tenantId, UUID documentId, JobOperation operation) {
        Instant now = Instant.now();
        IngestionJob job = new IngestionJob();
        job.id = UUID.randomUUID();
        job.tenantId = tenantId;
        job.documentId = documentId;
        job.operation = operation;
        job.status = JobStatus.PENDING;
        job.nextAttemptAt = now;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void running(String workerId) {
        status = JobStatus.RUNNING;
        attempts++;
        lockedAt = Instant.now();
        lockedBy = workerId;
        updatedAt = lockedAt;
    }

    public void succeeded() {
        status = JobStatus.SUCCEEDED;
        clearLease();
        updatedAt = Instant.now();
    }

    public void retry(String code) {
        lastErrorCode = code;
        status = attempts >= 8 ? JobStatus.DEAD : JobStatus.PENDING;
        long delay = Math.min(1_800, (long) Math.pow(2, Math.min(attempts, 10)) * 5);
        nextAttemptAt = Instant.now().plus(delay, ChronoUnit.SECONDS);
        clearLease();
        updatedAt = Instant.now();
    }

    public void failPermanently(String code) {
        lastErrorCode = code;
        status = JobStatus.DEAD;
        clearLease();
        updatedAt = Instant.now();
    }

    public void redrive() {
        if (status != JobStatus.DEAD) {
            throw new IllegalStateException("Only dead jobs can be redriven");
        }
        status = JobStatus.PENDING;
        attempts = 0;
        nextAttemptAt = Instant.now();
        lastErrorCode = null;
        clearLease();
        updatedAt = Instant.now();
    }

    public void cancelled() {
        status = JobStatus.CANCELLED;
        clearLease();
        updatedAt = Instant.now();
    }

    private void clearLease() {
        lockedAt = null;
        lockedBy = null;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public JobOperation getOperation() {
        return operation;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
