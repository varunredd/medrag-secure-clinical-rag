package com.medrag.api.tenant;

import com.medrag.api.ai.AiClient;
import com.medrag.api.audit.AuditService;
import com.medrag.api.document.ClinicalDocument;
import com.medrag.api.document.ClinicalDocumentRepository;
import com.medrag.api.document.DocumentService;
import com.medrag.api.document.DocumentStatus;
import com.medrag.api.document.FileValidator;
import com.medrag.api.job.IngestionJobRepository;
import com.medrag.api.malware.ClamAvScanner;
import com.medrag.api.query.QueryService;
import com.medrag.api.tenant.TenantSettingService;
import com.medrag.api.metering.UsageMeterService;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.storage.ObjectStorageService;
import com.medrag.api.web.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantIsolationServiceTest {
    @Mock ClinicalDocumentRepository documents;
    @Mock IngestionJobRepository jobs;
    @Mock FileValidator validator;
    @Mock ClamAvScanner scanner;
    @Mock ObjectStorageService storage;
    @Mock AuditService audit;
    @Mock AiClient ai;
    @Mock TenantSettingService tenantSettings;
    @Mock UsageMeterService meter;

    private final ClinicalPrincipal clinicA = new ClinicalPrincipal(
            "actor-a",
            "clinic-a",
            Set.of("DOCTOR")
    );

    @Test
    void documentListingAlwaysUsesAuthenticatedTenant() {
        PageRequest page = PageRequest.of(0, 20);
        when(documents.findAllByTenantIdAndDeletedAtIsNull("clinic-a", page))
                .thenReturn(Page.empty(page));

        documentService().list(clinicA, page);

        verify(documents).findAllByTenantIdAndDeletedAtIsNull("clinic-a", page);
        verify(documents, never()).findAllByTenantIdAndDeletedAtIsNull("clinic-b", page);
    }

    @Test
    void documentLookupCannotFallBackToUnscopedIdLookup() {
        UUID documentId = UUID.randomUUID();
        when(documents.findByIdAndTenantIdAndDeletedAtIsNull(documentId, "clinic-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService().get(clinicA, documentId))
                .hasMessage("DOCUMENT_NOT_FOUND");

        verify(documents).findByIdAndTenantIdAndDeletedAtIsNull(documentId, "clinic-a");
        verify(documents, never()).findById(documentId);
    }

    @Test
    void queryRejectsAnyDocumentOutsideReadyTenantScope() {
        UUID allowed = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(documents.countByIdInAndTenantIdAndDeletedAtIsNullAndStatus(
                any(),
                org.mockito.ArgumentMatchers.eq("clinic-a"),
                org.mockito.ArgumentMatchers.eq(DocumentStatus.READY)
        )).thenReturn(1L);

        QueryService service = new QueryService(documents, ai, audit, meter, tenantSettings);

        assertThatThrownBy(() -> service.query(
                clinicA,
                "Summarize medication changes",
                List.of(allowed, foreign),
                8
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("INVALID_OR_UNREADY_DOCUMENT_SCOPE");

        verify(ai, never()).query(any(), any());
    }

    private DocumentService documentService() {
        return new DocumentService(documents, jobs, validator, scanner, storage, audit, tenantSettings, meter);
    }
}
