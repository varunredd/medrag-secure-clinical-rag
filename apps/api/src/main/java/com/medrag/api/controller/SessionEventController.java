package com.medrag.api.controller;

import com.medrag.api.audit.AuditService;
import com.medrag.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/session-events")
public class SessionEventController {
    private final AuditService audit;

    public SessionEventController(AuditService audit) {
        this.audit = audit;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')")
    public ResponseEntity<Void> record(@Valid @RequestBody Request request) {
        var principal = CurrentPrincipal.require();
        audit.record(
                principal,
                "SESSION_" + request.event(),
                "SESSION",
                null,
                "SUCCESS",
                Map.of("source", "web-bff")
        );
        return ResponseEntity.noContent().build();
    }

    public record Request(
            @NotBlank @Pattern(regexp = "LOGIN|LOGOUT") String event
    ) {}
}
