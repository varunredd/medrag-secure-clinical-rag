package com.medrag.api.controller;

import com.medrag.api.document.ClinicalDocument;
import com.medrag.api.document.DocumentService;
import com.medrag.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN')")
    public ResponseEntity<DocumentView> upload(@RequestPart("file") MultipartFile file) {
        ClinicalDocument document = service.upload(CurrentPrincipal.require(), file);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/documents/" + document.getId()))
                .body(DocumentView.of(document));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')")
    public Page<DocumentView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(
                CurrentPrincipal.require(),
                PageRequest.of(
                        Math.max(0, page),
                        Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        ).map(DocumentView::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')")
    public DocumentView get(@PathVariable UUID id) {
        return DocumentView.of(service.get(CurrentPrincipal.require(), id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('DOCTOR','CLINIC_ADMIN')")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        service.retry(CurrentPrincipal.require(), id);
        return ResponseEntity.accepted().build();
    }

    @PatchMapping("/{id}/legal-hold")
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public DocumentView legalHold(@PathVariable UUID id, @Valid @RequestBody LegalHoldRequest request) {
        return DocumentView.of(service.setLegalHold(CurrentPrincipal.require(), id, request.enabled()));
    }

    @PatchMapping("/{id}/classification")
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public DocumentView classification(
            @PathVariable UUID id,
            @Valid @RequestBody ClassificationRequest request
    ) {
        return DocumentView.of(service.setClassification(
                CurrentPrincipal.require(),
                id,
                request.classification()
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(CurrentPrincipal.require(), id);
        return ResponseEntity.noContent().build();
    }

    public record LegalHoldRequest(boolean enabled) {}
    public record ClassificationRequest(@NotBlank String classification) {}

    public record DocumentView(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            String status,
            String failureCode,
            String classification,
            boolean legalHold,
            Instant retentionUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
        static DocumentView of(ClinicalDocument document) {
            return new DocumentView(
                    document.getId(),
                    document.getSafeFilename(),
                    document.getContentType(),
                    document.getSizeBytes(),
                    document.getStatus().name(),
                    document.getFailureCode(),
                    document.getClassification(),
                    document.isLegalHold(),
                    document.getRetentionUntil(),
                    document.getCreatedAt(),
                    document.getUpdatedAt()
            );
        }
    }
}
