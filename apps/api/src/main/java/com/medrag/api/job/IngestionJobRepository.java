package com.medrag.api.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
    long countByTenantIdAndStatus(String tenantId, JobStatus status);
    List<IngestionJob> findTop10ByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, JobStatus status);
    Optional<IngestionJob> findByIdAndTenantId(UUID id, String tenantId);
    Optional<IngestionJob> findTopByDocumentIdAndOperationAndStatusOrderByUpdatedAtDesc(
            UUID documentId,
            JobOperation operation,
            JobStatus status
    );

    @Query(value = """
            SELECT *
            FROM ingestion_job
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY CASE WHEN operation = 'PURGE' THEN 0 ELSE 1 END, next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<IngestionJob> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    boolean existsByDocumentIdAndOperationAndStatusIn(
            UUID documentId,
            JobOperation operation,
            Collection<JobStatus> statuses
    );

    java.util.Optional<IngestionJob> findByIdAndStatusAndLockedBy(
            UUID id,
            JobStatus status,
            String lockedBy
    );


    @Modifying
    @Query("""
            update IngestionJob j
               set j.status = com.medrag.api.job.JobStatus.PENDING,
                   j.nextAttemptAt = :now,
                   j.lockedAt = null,
                   j.lockedBy = null,
                   j.lastErrorCode = 'LEASE_EXPIRED',
                   j.updatedAt = :now
             where j.status = com.medrag.api.job.JobStatus.RUNNING
               and j.lockedAt < :cutoff
            """)
    int recoverExpiredLeases(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying
    @Query("""
            update IngestionJob j
               set j.lockedAt = :now,
                   j.updatedAt = :now
             where j.id = :jobId
               and j.status = com.medrag.api.job.JobStatus.RUNNING
               and j.lockedBy = :workerId
            """)
    int heartbeat(
            @Param("jobId") UUID jobId,
            @Param("workerId") String workerId,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            update IngestionJob j
               set j.status = com.medrag.api.job.JobStatus.CANCELLED,
                   j.lockedAt = null,
                   j.lockedBy = null,
                   j.updatedAt = :now
             where j.documentId = :documentId
               and j.operation = com.medrag.api.job.JobOperation.INGEST
               and j.status = com.medrag.api.job.JobStatus.PENDING
            """)
    int cancelPendingIngestions(@Param("documentId") UUID documentId, @Param("now") Instant now);
}
