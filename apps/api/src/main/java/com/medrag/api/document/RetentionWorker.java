package com.medrag.api.document;

import com.medrag.api.security.ClinicalPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class RetentionWorker {
    private static final Logger log = LoggerFactory.getLogger(RetentionWorker.class);

    private final ClinicalDocumentRepository documents;
    private final DocumentService service;

    public RetentionWorker(ClinicalDocumentRepository documents, DocumentService service) {
        this.documents = documents;
        this.service = service;
    }

    @Scheduled(cron = "${medrag.retention.cron:0 20 2 * * *}")
    public void purgeExpiredDocuments() {
        var due = documents.findTop100ByDeletedAtIsNullAndLegalHoldFalseAndRetentionUntilBeforeOrderByRetentionUntilAsc(
                Instant.now()
        );
        for (ClinicalDocument document : due) {
            try {
                service.delete(
                        new ClinicalPrincipal("retention-worker", document.getTenantId(), Set.of("SERVICE")),
                        document.getId()
                );
            } catch (RuntimeException error) {
                log.warn(
                        "Retention purge queue failed documentId={} tenant={} errorType={}",
                        document.getId(),
                        document.getTenantId(),
                        error.getClass().getSimpleName()
                );
            }
        }
    }
}
