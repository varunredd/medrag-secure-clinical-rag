package com.medrag.api.controller;

import com.medrag.api.security.CurrentPrincipal;
import com.medrag.api.tenant.TenantSetting;
import com.medrag.api.tenant.TenantSettingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tenant-settings")
public class TenantSettingController {
    private final TenantSettingService service;

    public TenantSettingController(TenantSettingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public View get() {
        return View.of(service.getOrCreate(CurrentPrincipal.require()));
    }

    @PutMapping
    @PreAuthorize("hasRole('CLINIC_ADMIN')")
    public View update(@Valid @RequestBody Request request) {
        return View.of(service.update(
                CurrentPrincipal.require(),
                new TenantSettingService.Update(
                        request.clinicName(),
                        request.retentionDays(),
                        request.maxUploadBytes(),
                        request.allowedMimeTypes(),
                        request.llmMode(),
                        request.llmEndpointRef(),
                        request.llmSecretRef(),
                        request.llmModel()
                )
        ));
    }

    public record Request(
            @NotBlank @Size(min = 2, max = 160) String clinicName,
            @Min(1) @Max(36500) Integer retentionDays,
            @Min(1048576) @Max(26214400) long maxUploadBytes,
            @NotEmpty Set<String> allowedMimeTypes,
            @NotBlank String llmMode,
            @Size(max = 500) String llmEndpointRef,
            @Size(max = 500) String llmSecretRef,
            @Size(max = 255) String llmModel
    ) {}

    public record View(
            String tenantId,
            String clinicName,
            Integer retentionDays,
            long maxUploadBytes,
            Set<String> allowedMimeTypes,
            String llmMode,
            String llmEndpointRef,
            String llmSecretRef,
            String llmModel,
            String updatedBy,
            Instant updatedAt
    ) {
        static View of(TenantSetting setting) {
            return new View(
                    setting.getTenantId(),
                    setting.getClinicName(),
                    setting.getRetentionDays(),
                    setting.getMaxUploadBytes(),
                    new LinkedHashSet<>(Arrays.asList(setting.getAllowedMimeTypes().split(","))),
                    setting.getLlmMode(),
                    setting.getLlmEndpointRef(),
                    setting.getLlmSecretRef(),
                    setting.getLlmModel(),
                    setting.getUpdatedBy(),
                    setting.getUpdatedAt()
            );
        }
    }
}
