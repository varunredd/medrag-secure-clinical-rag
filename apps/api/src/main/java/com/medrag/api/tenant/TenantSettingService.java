package com.medrag.api.tenant;

import com.medrag.api.audit.AuditService;
import com.medrag.api.config.MedRagProperties;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TenantSettingService {
    private static final Set<String> LLM_MODES = Set.of(
            "EXTRACTIVE",
            "PLATFORM_PRIVATE",
            "PRIVATE_OPENAI_COMPATIBLE"
    );

    private final TenantSettingRepository repository;
    private final MedRagProperties properties;
    private final AuditService audit;

    public TenantSettingService(
            TenantSettingRepository repository,
            MedRagProperties properties,
            AuditService audit
    ) {
        this.repository = repository;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public TenantSetting getOrCreate(ClinicalPrincipal principal) {
        return repository.findById(principal.tenantId()).orElseGet(() -> repository.save(
                TenantSetting.defaults(
                        principal.tenantId(),
                        principal.actorId(),
                        properties.uploads().maxBytes(),
                        String.join(",", new TreeSet<>(properties.uploads().allowedMimeTypes()))
                )
        ));
    }

    @Transactional(readOnly = true)
    public String clinicName(String tenantId) {
        return repository.findById(tenantId)
                .map(TenantSetting::getClinicName)
                .orElse(tenantId);
    }

    @Transactional(readOnly = true)
    public GenerationPolicy generationPolicy(String tenantId) {
        return repository.findById(tenantId)
                .map(setting -> new GenerationPolicy(
                        setting.getLlmMode(),
                        setting.getLlmEndpointRef(),
                        setting.getLlmSecretRef(),
                        setting.getLlmModel()
                ))
                .orElseGet(() -> new GenerationPolicy("PLATFORM_PRIVATE", null, null, null));
    }

    @Transactional(readOnly = true)
    public EffectivePolicy effectivePolicy(String tenantId) {
        return repository.findById(tenantId)
                .map(setting -> new EffectivePolicy(
                        setting.getMaxUploadBytes(),
                        parseMimeTypes(setting.getAllowedMimeTypes()),
                        setting.getRetentionDays()
                ))
                .orElseGet(() -> new EffectivePolicy(
                        properties.uploads().maxBytes(),
                        properties.uploads().allowedMimeTypes(),
                        null
                ));
    }

    @Transactional
    public TenantSetting update(ClinicalPrincipal principal, Update command) {
        validate(command);
        TenantSetting setting = getOrCreate(principal);
        String allowed = String.join(",", new TreeSet<>(command.allowedMimeTypes()));
        setting.update(
                command.clinicName().trim(),
                command.retentionDays(),
                command.maxUploadBytes(),
                allowed,
                command.llmMode(),
                command.llmEndpointRef(),
                command.llmSecretRef(),
                command.llmModel(),
                principal.actorId()
        );
        audit.record(
                principal,
                "TENANT_SETTINGS_UPDATE",
                "TENANT",
                principal.tenantId(),
                "SUCCESS",
                Map.of(
                        "retentionEnabled", command.retentionDays() != null,
                        "maxUploadBytes", command.maxUploadBytes(),
                        "allowedMimeCount", command.allowedMimeTypes().size(),
                        "llmMode", command.llmMode(),
                        "hasEndpointRef", command.llmEndpointRef() != null && !command.llmEndpointRef().isBlank(),
                        "hasSecretRef", command.llmSecretRef() != null && !command.llmSecretRef().isBlank()
                )
        );
        return setting;
    }

    private void validate(Update command) {
        if (command.clinicName() == null || command.clinicName().trim().length() < 2
                || command.clinicName().trim().length() > 160) {
            throw new BadRequestException("INVALID_CLINIC_NAME");
        }
        if (command.maxUploadBytes() < 1_048_576 || command.maxUploadBytes() > properties.uploads().maxBytes()) {
            throw new BadRequestException("INVALID_UPLOAD_LIMIT");
        }
        if (command.allowedMimeTypes() == null || command.allowedMimeTypes().isEmpty()
                || !properties.uploads().allowedMimeTypes().containsAll(command.allowedMimeTypes())) {
            throw new BadRequestException("INVALID_ALLOWED_MIME_TYPES");
        }
        if (!LLM_MODES.contains(command.llmMode())) {
            throw new BadRequestException("INVALID_LLM_MODE");
        }
        if ("PRIVATE_OPENAI_COMPATIBLE".equals(command.llmMode())) {
            if (isBlank(command.llmEndpointRef()) || isBlank(command.llmSecretRef()) || isBlank(command.llmModel())) {
                throw new BadRequestException("PRIVATE_LLM_REFS_REQUIRED");
            }
            if (!command.llmEndpointRef().startsWith("vault://") || !command.llmSecretRef().startsWith("vault://")) {
                throw new BadRequestException("PRIVATE_LLM_REFS_MUST_USE_VAULT_SCHEME");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Set<String> parseMimeTypes(String value) {
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    public record EffectivePolicy(long maxUploadBytes, Set<String> allowedMimeTypes, Integer retentionDays) {}
    public record GenerationPolicy(
            String mode,
            String endpointRef,
            String secretRef,
            String model
    ) {}

    public record Update(
            String clinicName,
            Integer retentionDays,
            long maxUploadBytes,
            Set<String> allowedMimeTypes,
            String llmMode,
            String llmEndpointRef,
            String llmSecretRef,
            String llmModel
    ) {}
}
