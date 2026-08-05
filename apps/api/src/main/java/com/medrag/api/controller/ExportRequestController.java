package com.medrag.api.controller;

import com.medrag.api.export.ExportRequest;
import com.medrag.api.export.ExportRequestService;
import com.medrag.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/export-requests")
public class ExportRequestController {
    private final ExportRequestService service;

    public ExportRequestController(ExportRequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public ResponseEntity<View> create(@Valid @RequestBody CreateRequest body) {
        ExportRequest request = service.create(
                CurrentPrincipal.require(), body.requestType(), body.subjectReference()
        );
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/export-requests/" + request.getId()))
                .body(View.of(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLINIC_ADMIN','AUDITOR')")
    public Page<View> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.list(
                CurrentPrincipal.require(),
                PageRequest.of(
                        Math.max(0, page),
                        Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        ).map(View::of);
    }

    public record CreateRequest(
            @NotBlank String requestType,
            @NotBlank @Size(max = 500) String subjectReference
    ) {}

    public record View(
            UUID id,
            String requestType,
            String status,
            String requestedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        static View of(ExportRequest request) {
            return new View(
                    request.getId(), request.getRequestType(), request.getStatus(),
                    request.getRequestedBy(), request.getCreatedAt(), request.getUpdatedAt()
            );
        }
    }
}
