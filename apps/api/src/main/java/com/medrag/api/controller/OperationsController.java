package com.medrag.api.controller;

import com.medrag.api.operations.OperationsService;
import com.medrag.api.security.CurrentPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService service;

    public OperationsController(OperationsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')")
    public OperationsService.Overview overview() {
        return service.overview(CurrentPrincipal.require());
    }

    @PostMapping("/jobs/{jobId}/redrive")
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public ResponseEntity<Void> redrive(@PathVariable UUID jobId) {
        service.redrive(CurrentPrincipal.require(), jobId);
        return ResponseEntity.accepted().build();
    }
}
