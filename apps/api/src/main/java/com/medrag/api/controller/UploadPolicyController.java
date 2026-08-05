package com.medrag.api.controller;

import com.medrag.api.security.CurrentPrincipal;
import com.medrag.api.tenant.TenantSettingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/upload-policy")
public class UploadPolicyController {
    private final TenantSettingService settings;

    public UploadPolicyController(TenantSettingService settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')")
    public View get() {
        var policy = settings.effectivePolicy(CurrentPrincipal.require().tenantId());
        return new View(policy.maxUploadBytes(), policy.allowedMimeTypes());
    }

    public record View(long maxUploadBytes, Set<String> allowedMimeTypes) {}
}
