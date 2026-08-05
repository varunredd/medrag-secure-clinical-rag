package com.medrag.api.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="audit_event")
public class AuditEvent {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false, length=120) private String tenantId;
    @Column(name="actor_id", nullable=false, length=120) private String actorId;
    @Column(name="actor_roles", nullable=false, length=500) private String actorRoles;
    @Column(nullable=false, length=80) private String action;
    @Column(name="resource_type", nullable=false, length=80) private String resourceType;
    @Column(name="resource_id", length=120) private String resourceId;
    @Column(nullable=false, length=20) private String outcome;
    @Column(name="request_id", nullable=false, length=100) private String requestId;
    @Column(name="source_ip_hash", length = 64) private String sourceIpHash;
    @Column(name="metadata_json", nullable=false, columnDefinition="TEXT") private String metadataJson;
    @Column(name="occurred_at", nullable=false) private Instant occurredAt;
    protected AuditEvent() {}
    public static AuditEvent of(String tenant, String actor, String roles, String action, String resourceType,
                                String resourceId, String outcome, String requestId, String metadataJson) {
        AuditEvent e=new AuditEvent(); e.id=UUID.randomUUID(); e.tenantId=tenant; e.actorId=actor; e.actorRoles=roles;
        e.action=action; e.resourceType=resourceType; e.resourceId=resourceId; e.outcome=outcome; e.requestId=requestId;
        e.metadataJson=metadataJson; e.occurredAt=Instant.now(); return e;
    }
    public UUID getId(){return id;} public String getTenantId(){return tenantId;} public String getActorId(){return actorId;}
    public String getActorRoles(){return actorRoles;} public String getAction(){return action;} public String getResourceType(){return resourceType;}
    public String getResourceId(){return resourceId;} public String getOutcome(){return outcome;} public String getRequestId(){return requestId;}
    public String getMetadataJson(){return metadataJson;} public Instant getOccurredAt(){return occurredAt;}
}
