package com.medrag.api.metering;

import com.medrag.api.security.ClinicalPrincipal;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageMeterService {
    private final UsageMeterEventRepository repository;

    public UsageMeterService(UsageMeterEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void emit(
            ClinicalPrincipal principal,
            String eventType,
            long quantity,
            String resourceId
    ) {
        repository.save(UsageMeterEvent.of(
                principal.tenantId(),
                principal.actorId(),
                eventType,
                quantity,
                resourceId,
                MDC.get("requestId") == null ? "unknown" : MDC.get("requestId")
        ));
    }
}
