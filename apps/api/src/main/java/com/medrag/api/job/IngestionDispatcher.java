package com.medrag.api.job;

import com.medrag.api.ai.AiClient;
import com.medrag.api.document.ClinicalDocument;
import com.medrag.api.document.ClinicalDocumentRepository;
import com.medrag.api.security.ClinicalPrincipal;
import com.medrag.api.storage.ObjectStorageService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class IngestionDispatcher {
    private static final Logger log = LoggerFactory.getLogger(IngestionDispatcher.class);
    private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(408, 425, 429, 500, 502, 503, 504);

    private final IngestionJobRepository jobs;
    private final ClinicalDocumentRepository documents;
    private final AiClient ai;
    private final ObjectStorageService storage;
    private final TransactionTemplate tx;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final String workerId = UUID.randomUUID().toString();
    private final ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService jobExecutor;
    private final Semaphore jobSlots;

    public IngestionDispatcher(
            IngestionJobRepository jobs,
            ClinicalDocumentRepository documents,
            AiClient ai,
            ObjectStorageService storage,
            PlatformTransactionManager transactionManager,
            @Value("${medrag.jobs.lease-duration:PT10M}") Duration leaseDuration,
            @Value("${medrag.jobs.heartbeat-interval:PT1M}") Duration heartbeatInterval,
            @Value("${medrag.jobs.parallelism:4}") int parallelism
    ) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Job lease duration must be positive");
        }
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()
                || heartbeatInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException("Job heartbeat interval must be positive and shorter than the lease");
        }
        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalArgumentException("Job parallelism must be between 1 and 32");
        }

        this.jobs = jobs;
        this.documents = documents;
        this.ai = ai;
        this.storage = storage;
        this.tx = new TransactionTemplate(transactionManager);
        this.leaseDuration = leaseDuration;
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(heartbeatThreadFactory());
        this.jobExecutor = Executors.newFixedThreadPool(parallelism, jobThreadFactory());
        this.jobSlots = new Semaphore(parallelism);
    }

    @Scheduled(fixedDelayString = "${medrag.jobs.poll-delay:5000}")
    public void poll() {
        int capacity = jobSlots.availablePermits();
        if (capacity <= 0) {
            return;
        }

        Instant now = Instant.now();
        List<UUID> claimed = tx.execute(status -> {
            int recovered = jobs.recoverExpiredLeases(now.minus(leaseDuration), now);
            if (recovered > 0) {
                log.warn("Recovered {} expired ingestion job leases", recovered);
            }
            List<IngestionJob> due = jobs.lockDue(now, capacity);
            due.forEach(job -> job.running(workerId));
            return due.stream().map(IngestionJob::getId).toList();
        });
        if (claimed == null) {
            return;
        }

        claimed.forEach(jobId -> {
            if (!jobSlots.tryAcquire()) {
                log.error("Ingestion capacity changed after claim jobId={}", jobId);
                return;
            }
            jobExecutor.submit(() -> {
                try {
                    execute(jobId);
                } catch (Exception error) {
                    log.error("Unexpected ingestion worker failure jobId={} errorType={}",
                            jobId, error.getClass().getSimpleName());
                } finally {
                    jobSlots.release();
                }
            });
        });
    }

    private void execute(UUID jobId) {
        JobSnapshot snapshot = tx.execute(status -> {
            IngestionJob job = jobs.findByIdAndStatusAndLockedBy(jobId, JobStatus.RUNNING, workerId)
                    .orElseThrow(() -> new IllegalStateException("Ingestion job lease is not owned by this worker"));
            ClinicalDocument document = documents.findById(job.getDocumentId()).orElseThrow();
            if (job.getOperation() == JobOperation.INGEST && document.getDeletedAt() != null) {
                job.cancelled();
                return null;
            }
            return new JobSnapshot(
                    job.getTenantId(),
                    job.getOperation(),
                    document.getId(),
                    document.getObjectKey(),
                    document.getContentType(),
                    document.getSha256()
            );
        });
        if (snapshot == null) {
            return;
        }

        ScheduledFuture<?> heartbeat = startHeartbeat(jobId);
        ClinicalPrincipal servicePrincipal = new ClinicalPrincipal(
                "ingestion-dispatcher",
                snapshot.tenantId(),
                Set.of("SERVICE")
        );
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("tenantId", snapshot.tenantId());
        MDC.put("actorId", servicePrincipal.actorId());

        try {
            if (snapshot.operation() == JobOperation.INGEST) {
                tx.executeWithoutResult(status -> documents.findById(snapshot.documentId())
                        .orElseThrow()
                        .markProcessing());
                ai.ingest(servicePrincipal, new AiClient.IngestRequest(
                        snapshot.tenantId(),
                        snapshot.documentId(),
                        snapshot.objectKey(),
                        snapshot.contentType(),
                        snapshot.sha256()
                ));
            } else {
                ai.purge(servicePrincipal, new AiClient.PurgeRequest(snapshot.tenantId(), snapshot.documentId()));
                storage.delete(snapshot.objectKey());
            }

            tx.executeWithoutResult(status -> {
                IngestionJob job = jobs.findByIdAndStatusAndLockedBy(jobId, JobStatus.RUNNING, workerId)
                        .orElse(null);
                if (job == null) {
                    log.warn("Discarding stale ingestion completion after lease loss jobId={}", jobId);
                    return;
                }
                job.succeeded();
                if (snapshot.operation() == JobOperation.INGEST) {
                    ClinicalDocument document = documents.findById(snapshot.documentId()).orElseThrow();
                    if (document.getDeletedAt() == null) {
                        document.markReady();
                    }
                }
            });
        } catch (Exception error) {
            Failure failure = classify(error);
            tx.executeWithoutResult(status -> {
                IngestionJob job = jobs.findByIdAndStatusAndLockedBy(jobId, JobStatus.RUNNING, workerId)
                        .orElse(null);
                if (job == null) {
                    log.warn("Discarding stale ingestion failure after lease loss jobId={}", jobId);
                    return;
                }
                if (failure.retryable()) {
                    job.retry(failure.code());
                } else {
                    job.failPermanently(failure.code());
                }
                if (snapshot.operation() == JobOperation.INGEST) {
                    ClinicalDocument document = documents.findById(snapshot.documentId()).orElseThrow();
                    if (document.getDeletedAt() == null) {
                        document.markFailed(failure.code());
                    }
                }
            });
            log.warn(
                    "AI job failed jobId={} documentId={} code={} retryable={}",
                    jobId,
                    snapshot.documentId(),
                    failure.code(),
                    failure.retryable()
            );
        } finally {
            heartbeat.cancel(false);
            MDC.clear();
        }
    }

    private ScheduledFuture<?> startHeartbeat(UUID jobId) {
        long intervalMillis = heartbeatInterval.toMillis();
        return heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        Integer updated = tx.execute(status -> jobs.heartbeat(jobId, workerId, Instant.now()));
                        if (updated == null || updated == 0) {
                            log.debug("Stopped extending inactive ingestion lease jobId={}", jobId);
                        }
                    } catch (Exception error) {
                        log.warn("Could not extend ingestion lease jobId={}", jobId);
                    }
                },
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private Failure classify(Exception error) {
        if (error instanceof WebClientResponseException responseError) {
            int status = responseError.getStatusCode().value();
            return new Failure("AI_HTTP_" + status, RETRYABLE_HTTP_STATUSES.contains(status));
        }
        return new Failure("AI_UNAVAILABLE", true);
    }

    @PreDestroy
    void shutdownExecutors() {
        jobExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    private static ThreadFactory heartbeatThreadFactory() {
        return daemonThreadFactory("medrag-ingestion-heartbeat");
    }

    private static ThreadFactory jobThreadFactory() {
        return daemonThreadFactory("medrag-ingestion-worker");
    }

    private static ThreadFactory daemonThreadFactory(String namePrefix) {
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, namePrefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record JobSnapshot(
            String tenantId,
            JobOperation operation,
            UUID documentId,
            String objectKey,
            String contentType,
            String sha256
    ) {
    }

    private record Failure(String code, boolean retryable) {
    }
}
