package com.medrag.api.operations;

import com.medrag.api.audit.AuditService;
import com.medrag.api.document.ClinicalDocument;
import com.medrag.api.document.ClinicalDocumentRepository;
import com.medrag.api.document.DocumentStatus;
import com.medrag.api.job.IngestionJob;
import com.medrag.api.job.IngestionJobRepository;
import com.medrag.api.job.JobStatus;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.tenant.TenantSettingService;
import com.medrag.api.web.BadRequestException;
import com.medrag.api.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationsService {
    private final ClinicalDocumentRepository documents;
    private final IngestionJobRepository jobs;
    private final AuditService audit;
    private final TenantSettingService tenantSettings;

    public OperationsService(
            ClinicalDocumentRepository documents,
            IngestionJobRepository jobs,
            AuditService audit,
            TenantSettingService tenantSettings
    ) {
        this.documents = documents;
        this.jobs = jobs;
        this.audit = audit;
        this.tenantSettings = tenantSettings;
    }

    @Transactional(readOnly = true)
    public Overview overview(ClinicalPrincipal principal) {
        String tenant = principal.tenantId();
        long total = documents.countByTenantIdAndDeletedAtIsNull(tenant);
        long ready = documents.countByTenantIdAndDeletedAtIsNullAndStatus(tenant, DocumentStatus.READY);
        long queued = documents.countByTenantIdAndDeletedAtIsNullAndStatus(tenant, DocumentStatus.QUEUED);
        long processing = documents.countByTenantIdAndDeletedAtIsNullAndStatus(tenant, DocumentStatus.PROCESSING);
        long failed = documents.countByTenantIdAndDeletedAtIsNullAndStatus(tenant, DocumentStatus.FAILED);
        long pendingJobs = jobs.countByTenantIdAndStatus(tenant, JobStatus.PENDING);
        long runningJobs = jobs.countByTenantIdAndStatus(tenant, JobStatus.RUNNING);
        long deadJobs = jobs.countByTenantIdAndStatus(tenant, JobStatus.DEAD);

        List<DocumentSummary> recent = documents
                .findTop8ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenant)
                .stream()
                .map(DocumentSummary::of)
                .toList();
        List<DeadJobSummary> dead = jobs
                .findTop10ByTenantIdAndStatusOrderByUpdatedAtDesc(tenant, JobStatus.DEAD)
                .stream()
                .map(DeadJobSummary::of)
                .toList();

        return new Overview(
                tenantSettings.clinicName(tenant),
                new DocumentCounts(total, ready, queued, processing, failed),
                new PipelineCounts(pendingJobs, runningJobs, deadJobs),
                recent,
                dead,
                Instant.now()
        );
    }

    @Transactional
    public void redrive(ClinicalPrincipal principal, UUID jobId) {
        IngestionJob job = jobs.findByIdAndTenantId(jobId, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("JOB_NOT_FOUND"));
        if (job.getStatus() != JobStatus.DEAD) {
            throw new BadRequestException("JOB_NOT_DEAD");
        }
        if (jobs.existsByDocumentIdAndOperationAndStatusIn(
                job.getDocumentId(),
                job.getOperation(),
                List.of(JobStatus.PENDING, JobStatus.RUNNING)
        )) {
            throw new BadRequestException("JOB_ALREADY_ACTIVE");
        }
        job.redrive();
        audit.record(
                principal,
                "INGESTION_JOB_REDRIVE",
                "INGESTION_JOB",
                jobId.toString(),
                "SUCCESS",
                Map.of("documentId", job.getDocumentId().toString(), "operation", job.getOperation().name())
        );
    }

    public record Overview(
            String clinicName,
            DocumentCounts documents,
            PipelineCounts pipeline,
            List<DocumentSummary> recentDocuments,
            List<DeadJobSummary> deadJobs,
            Instant generatedAt
    ) {}

    public record DocumentCounts(long total, long ready, long queued, long processing, long failed) {}
    public record PipelineCounts(long pending, long running, long dead) {}

    public record DocumentSummary(
            UUID id,
            String filename,
            String status,
            String failureCode,
            long sizeBytes,
            Instant createdAt,
            Instant updatedAt
    ) {
        static DocumentSummary of(ClinicalDocument document) {
            return new DocumentSummary(
                    document.getId(),
                    document.getSafeFilename(),
                    document.getStatus().name(),
                    document.getFailureCode(),
                    document.getSizeBytes(),
                    document.getCreatedAt(),
                    document.getUpdatedAt()
            );
        }
    }

    public record DeadJobSummary(
            UUID id,
            UUID documentId,
            String operation,
            int attempts,
            String lastErrorCode,
            Instant updatedAt
    ) {
        static DeadJobSummary of(IngestionJob job) {
            return new DeadJobSummary(
                    job.getId(),
                    job.getDocumentId(),
                    job.getOperation().name(),
                    job.getAttempts(),
                    job.getLastErrorCode(),
                    job.getUpdatedAt()
            );
        }
    }
}
