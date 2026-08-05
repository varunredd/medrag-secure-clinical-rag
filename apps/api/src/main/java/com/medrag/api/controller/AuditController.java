package com.medrag.api.controller;

import com.medrag.api.audit.AuditEvent;
import com.medrag.api.audit.AuditEventRepository;
import com.medrag.api.security.CurrentPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    private final AuditEventRepository repository;
    public AuditController(AuditEventRepository repository){this.repository=repository;}
    @GetMapping @PreAuthorize("hasAnyRole('CLINIC_ADMIN','AUDITOR')")
    public Page<View> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        String tenant=CurrentPrincipal.require().tenantId();
        return repository.findAllByTenantId(tenant,PageRequest.of(Math.max(0,page),Math.min(Math.max(size,1),100),Sort.by(Sort.Direction.DESC,"occurredAt"))).map(View::of);
    }
    public record View(UUID id,String actorId,String actorRoles,String action,String resourceType,String resourceId,String outcome,String requestId,String metadataJson,Instant occurredAt){
        static View of(AuditEvent e){return new View(e.getId(),e.getActorId(),e.getActorRoles(),e.getAction(),e.getResourceType(),e.getResourceId(),e.getOutcome(),e.getRequestId(),e.getMetadataJson(),e.getOccurredAt());}
    }
}
