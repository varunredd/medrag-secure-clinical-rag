package com.medrag.api.export;

import com.medrag.api.audit.AuditService;
import com.medrag.api.document.Hashing;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.web.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExportRequestService {
    private static final Set<String> TYPES = Set.of("DATA_EXPORT", "DSAR");

    private final ExportRequestRepository repository;
    private final AuditService audit;

    public ExportRequestService(ExportRequestRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public ExportRequest create(ClinicalPrincipal principal, String requestType, String subjectReference) {
        String normalizedType = requestType == null ? "" : requestType.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalizedType)) {
            throw new BadRequestException("INVALID_EXPORT_REQUEST_TYPE");
        }
        String normalizedReference = subjectReference == null ? "" : subjectReference.trim().toLowerCase(Locale.ROOT);
        if (normalizedReference.length() < 2 || normalizedReference.length() > 500) {
            throw new BadRequestException("INVALID_SUBJECT_REFERENCE");
        }
        String hash = Hashing.sha256(normalizedReference.getBytes(StandardCharsets.UTF_8));
        ExportRequest request = repository.save(ExportRequest.create(
                principal.tenantId(), principal.actorId(), normalizedType, hash
        ));
        audit.record(
                principal,
                "EXPORT_REQUEST_CREATE",
                "EXPORT_REQUEST",
                request.getId().toString(),
                "SUCCESS",
                Map.of("requestType", normalizedType, "subjectReferenceStored", "SHA256_ONLY")
        );
        return request;
    }

    @Transactional(readOnly = true)
    public Page<ExportRequest> list(ClinicalPrincipal principal, Pageable pageable) {
        return repository.findAllByTenantId(principal.tenantId(), pageable);
    }
}
