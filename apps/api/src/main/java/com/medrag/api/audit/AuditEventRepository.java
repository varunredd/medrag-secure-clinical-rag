package com.medrag.api.audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Page<AuditEvent> findAllByTenantId(String tenantId, Pageable pageable);
}
