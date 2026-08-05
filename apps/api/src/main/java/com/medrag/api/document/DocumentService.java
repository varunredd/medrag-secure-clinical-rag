package com.medrag.api.document;

import com.medrag.api.audit.AuditService;
import com.medrag.api.job.IngestionJob;
import com.medrag.api.job.IngestionJobRepository;
import com.medrag.api.job.JobOperation;
import com.medrag.api.job.JobStatus;
import com.medrag.api.malware.ClamAvScanner;
import com.medrag.api.metering.UsageMeterService;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.storage.ObjectStorageService;
import com.medrag.api.tenant.TenantSettingService;
import com.medrag.api.web.BadRequestException;
import com.medrag.api.web.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {
    private static final List<JobStatus> ACTIVE_JOB_STATUSES = List.of(JobStatus.PENDING, JobStatus.RUNNING);
    private static final Set<String> CLASSIFICATIONS = Set.of("PHI_RESTRICTED", "CLINICAL_CONFIDENTIAL", "DEIDENTIFIED", "ADMINISTRATIVE");

    private final ClinicalDocumentRepository documents;
    private final IngestionJobRepository jobs;
    private final FileValidator validator;
    private final ClamAvScanner scanner;
    private final ObjectStorageService storage;
    private final AuditService audit;
    private final TenantSettingService tenantSettings;
    private final UsageMeterService meter;

    public DocumentService(
            ClinicalDocumentRepository documents,
            IngestionJobRepository jobs,
            FileValidator validator,
            ClamAvScanner scanner,
            ObjectStorageService storage,
            AuditService audit,
            TenantSettingService tenantSettings,
            UsageMeterService meter
    ) {
        this.documents = documents;
        this.jobs = jobs;
        this.validator = validator;
        this.scanner = scanner;
        this.storage = storage;
        this.audit = audit;
        this.tenantSettings = tenantSettings;
        this.meter = meter;
    }

    @Transactional
    public ClinicalDocument upload(ClinicalPrincipal principal, MultipartFile file) {
        String objectKey = null;
        try {
            byte[] bytes = file.getBytes();
            TenantSettingService.EffectivePolicy policy = tenantSettings.effectivePolicy(principal.tenantId());
            FileValidator.ValidatedFile validated = validator.validate(
                    file,
                    bytes,
                    policy.maxUploadBytes(),
                    policy.allowedMimeTypes()
            );
            scanner.assertClean(bytes);

            String sha256 = Hashing.sha256(bytes);
            UUID documentId = UUID.randomUUID();
            objectKey = "tenants/" + principal.tenantId() + "/documents/" + documentId + "/source";
            String displayName = "clinical-record-" + documentId + "." + validated.extension();
            Instant retentionUntil = policy.retentionDays() == null
                    ? null
                    : Instant.now().plus(policy.retentionDays(), ChronoUnit.DAYS);

            storage.put(
                    objectKey,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    validated.contentType(),
                    sha256
            );
            registerRollbackCleanup(objectKey);

            ClinicalDocument document = ClinicalDocument.create(
                    documentId,
                    principal.tenantId(),
                    displayName,
                    validated.contentType(),
                    bytes.length,
                    sha256,
                    objectKey,
                    principal.actorId(),
                    retentionUntil
            );
            documents.save(document);
            jobs.save(IngestionJob.pending(principal.tenantId(), document.getId(), JobOperation.INGEST));
            audit.record(
                    principal,
                    "DOCUMENT_UPLOAD",
                    "DOCUMENT",
                    document.getId().toString(),
                    "SUCCESS",
                    Map.of(
                            "sizeBytes", bytes.length,
                            "classification", document.getClassification(),
                            "retentionEnabled", retentionUntil != null
                    )
            );
            meter.emit(principal, "DOCUMENT_UPLOAD_BYTES", bytes.length, document.getId().toString());
            return document;
        } catch (RuntimeException error) {
            deleteOrphanBestEffort(objectKey);
            audit.recordFailure(principal, "DOCUMENT_UPLOAD", "DOCUMENT", null, Map.of("code", "UPLOAD_FAILED"));
            throw error;
        } catch (Exception error) {
            deleteOrphanBestEffort(objectKey);
            audit.recordFailure(principal, "DOCUMENT_UPLOAD", "DOCUMENT", null, Map.of("code", "UPLOAD_FAILED"));
            throw new IllegalStateException("Upload failed", error);
        }
    }

    @Transactional(readOnly = true)
    public Page<ClinicalDocument> list(ClinicalPrincipal principal, Pageable pageable) {
        return documents.findAllByTenantIdAndDeletedAtIsNull(principal.tenantId(), pageable);
    }

    @Transactional(readOnly = true)
    public ClinicalDocument get(ClinicalPrincipal principal, UUID id) {
        return documents.findByIdAndTenantIdAndDeletedAtIsNull(id, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND"));
    }

    @Transactional
    public void retry(ClinicalPrincipal principal, UUID id) {
        ClinicalDocument document = get(principal, id);
        if (!document.canRetry()) {
            throw new BadRequestException("DOCUMENT_NOT_RETRYABLE");
        }
        boolean active = jobs.existsByDocumentIdAndOperationAndStatusIn(
                document.getId(),
                JobOperation.INGEST,
                ACTIVE_JOB_STATUSES
        );
        boolean redriven = false;
        if (!active) {
            var dead = jobs.findTopByDocumentIdAndOperationAndStatusOrderByUpdatedAtDesc(
                    document.getId(), JobOperation.INGEST, JobStatus.DEAD
            );
            if (dead.isPresent()) {
                dead.get().redrive();
                redriven = true;
            } else {
                jobs.save(IngestionJob.pending(principal.tenantId(), document.getId(), JobOperation.INGEST));
            }
        }
        audit.record(
                principal,
                "DOCUMENT_RETRY",
                "DOCUMENT",
                id.toString(),
                "SUCCESS",
                Map.of("queued", !active, "reusedDeadLetter", redriven)
        );
    }

    @Transactional
    public void delete(ClinicalPrincipal principal, UUID id) {
        ClinicalDocument document = get(principal, id);
        if (document.isLegalHold()) {
            throw new BadRequestException("DOCUMENT_UNDER_LEGAL_HOLD");
        }
        document.markDeleted();
        jobs.cancelPendingIngestions(document.getId(), Instant.now());
        boolean purgeActive = jobs.existsByDocumentIdAndOperationAndStatusIn(
                document.getId(),
                JobOperation.PURGE,
                ACTIVE_JOB_STATUSES
        );
        if (!purgeActive) {
            jobs.save(IngestionJob.pending(principal.tenantId(), document.getId(), JobOperation.PURGE));
        }
        audit.record(principal, "DOCUMENT_DELETE", "DOCUMENT", id.toString(), "SUCCESS", Map.of(
                "source", principal.roles().contains("SERVICE") ? "retention-worker" : "user"
        ));
    }

    @Transactional
    public ClinicalDocument setLegalHold(ClinicalPrincipal principal, UUID id, boolean enabled) {
        ClinicalDocument document = get(principal, id);
        document.setLegalHold(enabled);
        audit.record(
                principal,
                enabled ? "DOCUMENT_LEGAL_HOLD_APPLY" : "DOCUMENT_LEGAL_HOLD_RELEASE",
                "DOCUMENT",
                id.toString(),
                "SUCCESS",
                Map.of()
        );
        return document;
    }

    @Transactional
    public ClinicalDocument setClassification(ClinicalPrincipal principal, UUID id, String classification) {
        String normalized = classification == null ? "" : classification.trim().toUpperCase();
        if (!CLASSIFICATIONS.contains(normalized)) {
            throw new BadRequestException("INVALID_DOCUMENT_CLASSIFICATION");
        }
        ClinicalDocument document = get(principal, id);
        document.setClassification(normalized);
        audit.record(
                principal,
                "DOCUMENT_CLASSIFICATION_UPDATE",
                "DOCUMENT",
                id.toString(),
                "SUCCESS",
                Map.of("classification", normalized)
        );
        return document;
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteOrphanBestEffort(objectKey);
                }
            }
        });
    }

    private void deleteOrphanBestEffort(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // A lifecycle policy should clean up any object that cannot be removed synchronously.
        }
    }
}
