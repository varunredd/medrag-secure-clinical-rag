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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentGovernanceServiceTest {
    @Mock ClinicalDocumentRepository documents;
    @Mock IngestionJobRepository jobs;
    @Mock FileValidator validator;
    @Mock ClamAvScanner scanner;
    @Mock ObjectStorageService storage;
    @Mock AuditService audit;
    @Mock TenantSettingService tenantSettings;
    @Mock UsageMeterService meter;

    private final ClinicalPrincipal admin = new ClinicalPrincipal(
            "admin-a", "clinic-a", Set.of("CLINIC_ADMIN")
    );

    @Test
    void legalHoldBlocksSoftDeleteAndPurgeCreation() {
        ClinicalDocument document = document();
        document.setLegalHold(true);
        when(documents.findByIdAndTenantIdAndDeletedAtIsNull(document.getId(), "clinic-a"))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service().delete(admin, document.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("DOCUMENT_UNDER_LEGAL_HOLD");

        verify(jobs, never()).cancelPendingIngestions(
                org.mockito.ArgumentMatchers.eq(document.getId()),
                org.mockito.ArgumentMatchers.any(java.time.Instant.class)
        );
        verify(jobs, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(document.getDeletedAt()).isNull();
    }

    @Test
    void retryReusesLatestDeadLetterAndResetsRetryBudget() {
        ClinicalDocument document = document();
        document.markFailed("EMBEDDING_TIMEOUT");
        IngestionJob dead = IngestionJob.pending("clinic-a", document.getId(), JobOperation.INGEST);
        dead.failPermanently("EMBEDDING_TIMEOUT");

        when(documents.findByIdAndTenantIdAndDeletedAtIsNull(document.getId(), "clinic-a"))
                .thenReturn(Optional.of(document));
        when(jobs.existsByDocumentIdAndOperationAndStatusIn(
                document.getId(), JobOperation.INGEST, List.of(JobStatus.PENDING, JobStatus.RUNNING)
        )).thenReturn(false);
        when(jobs.findTopByDocumentIdAndOperationAndStatusOrderByUpdatedAtDesc(
                document.getId(), JobOperation.INGEST, JobStatus.DEAD
        )).thenReturn(Optional.of(dead));

        service().retry(admin, document.getId());

        assertThat(dead.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(dead.getAttempts()).isZero();
        assertThat(dead.getLastErrorCode()).isNull();
        verify(jobs, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ClinicalDocument document() {
        UUID id = UUID.randomUUID();
        return ClinicalDocument.create(
                id,
                "clinic-a",
                "clinical-record-" + id + ".txt",
                "text/plain",
                42,
                "a".repeat(64),
                "tenants/clinic-a/documents/" + id + "/source",
                "doctor-a",
                null
        );
    }

    private DocumentService service() {
        return new DocumentService(
                documents, jobs, validator, scanner, storage, audit, tenantSettings, meter
        );
    }
}
