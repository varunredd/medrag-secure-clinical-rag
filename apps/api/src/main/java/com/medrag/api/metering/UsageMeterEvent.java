package com.medrag.api.metering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_meter_event")
public class UsageMeterEvent {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 120) private String tenantId;
    @Column(name = "actor_id", nullable = false, length = 120) private String actorId;
    @Column(name = "event_type", nullable = false, length = 60) private String eventType;
    @Column(nullable = false) private long quantity;
    @Column(name = "resource_id", length = 120) private String resourceId;
    @Column(name = "request_id", nullable = false, length = 100) private String requestId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected UsageMeterEvent() {}

    public static UsageMeterEvent of(
            String tenantId,
            String actorId,
            String eventType,
            long quantity,
            String resourceId,
            String requestId
    ) {
        UsageMeterEvent event = new UsageMeterEvent();
        event.id = UUID.randomUUID();
        event.tenantId = tenantId;
        event.actorId = actorId;
        event.eventType = eventType;
        event.quantity = quantity;
        event.resourceId = resourceId;
        event.requestId = requestId;
        event.occurredAt = Instant.now();
        return event;
    }
}
