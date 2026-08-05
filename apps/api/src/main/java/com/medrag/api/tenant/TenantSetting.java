package com.medrag.api.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "tenant_setting")
public class TenantSetting {
    @Id
    @Column(name = "tenant_id", nullable = false, length = 120)
    private String tenantId;

    @Column(name = "clinic_name", nullable = false, length = 160)
    private String clinicName;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "max_upload_bytes", nullable = false)
    private long maxUploadBytes;

    @Column(name = "allowed_mime_types", nullable = false, length = 600)
    private String allowedMimeTypes;

    @Column(name = "llm_mode", nullable = false, length = 40)
    private String llmMode;

    @Column(name = "llm_endpoint_ref", length = 500)
    private String llmEndpointRef;

    @Column(name = "llm_secret_ref", length = 500)
    private String llmSecretRef;

    @Column(name = "llm_model", length = 255)
    private String llmModel;

    @Column(name = "updated_by", nullable = false, length = 120)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TenantSetting() {}

    public static TenantSetting defaults(
            String tenantId,
            String actorId,
            long maxUploadBytes,
            String allowedMimeTypes
    ) {
        TenantSetting setting = new TenantSetting();
        setting.tenantId = tenantId;
        setting.clinicName = tenantId;
        setting.retentionDays = null;
        setting.maxUploadBytes = maxUploadBytes;
        setting.allowedMimeTypes = allowedMimeTypes;
        setting.llmMode = "PLATFORM_PRIVATE";
        setting.updatedBy = actorId;
        setting.updatedAt = Instant.now();
        return setting;
    }

    public void update(
            String clinicName,
            Integer retentionDays,
            long maxUploadBytes,
            String allowedMimeTypes,
            String llmMode,
            String llmEndpointRef,
            String llmSecretRef,
            String llmModel,
            String actorId
    ) {
        this.clinicName = clinicName;
        this.retentionDays = retentionDays;
        this.maxUploadBytes = maxUploadBytes;
        this.allowedMimeTypes = allowedMimeTypes;
        this.llmMode = llmMode;
        this.llmEndpointRef = blankToNull(llmEndpointRef);
        this.llmSecretRef = blankToNull(llmSecretRef);
        this.llmModel = blankToNull(llmModel);
        this.updatedBy = actorId;
        this.updatedAt = Instant.now();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getTenantId() { return tenantId; }
    public String getClinicName() { return clinicName; }
    public Integer getRetentionDays() { return retentionDays; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public String getAllowedMimeTypes() { return allowedMimeTypes; }
    public String getLlmMode() { return llmMode; }
    public String getLlmEndpointRef() { return llmEndpointRef; }
    public String getLlmSecretRef() { return llmSecretRef; }
    public String getLlmModel() { return llmModel; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
