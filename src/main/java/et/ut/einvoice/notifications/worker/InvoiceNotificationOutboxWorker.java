package et.ut.einvoice.notifications.worker;

import et.ut.einvoice.audit.domain.AuditAction;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.notifications.domain.FailureClassification;
import et.ut.einvoice.notifications.domain.InvoiceNotificationOutbox;
import et.ut.einvoice.notifications.domain.TenantSmsQuota;
import et.ut.einvoice.notifications.metrics.SmsMetrics;
import et.ut.einvoice.notifications.provider.SmsDeliveryStatus;
import et.ut.einvoice.notifications.provider.SmsProvider;
import et.ut.einvoice.notifications.provider.SmsSendRequest;
import et.ut.einvoice.notifications.provider.SmsSendResult;
import et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository;
import et.ut.einvoice.notifications.repository.TenantSmsQuotaRepository;
import et.ut.einvoice.notifications.service.SmsSegmentationCalculator;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.security.TenantConnectionPreparer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous background worker claiming and dispatching durable outbox notifications.
 * Enforces PostgreSQL RLS connection-pool discipline, circuit breaker protection,
 * atomic daily quotas, CAS lease ownership protection, and provider capability adaptation.
 */
@Component
public class InvoiceNotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNotificationOutboxWorker.class);

    private final InvoiceNotificationOutboxRepository outboxRepository;
    private final TenantSmsQuotaRepository quotaRepository;
    private final SmsProvider smsProvider;
    private final AuditService auditService;
    private final SmsMetrics smsMetrics;
    private final TenantConnectionPreparer tenantConnectionPreparer;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    private final String workerId;
    private final AtomicBoolean circuitBreakerOpen = new AtomicBoolean(false);
    private Instant circuitBreakerOpenedAt = null;

    @Value("${notifications.sms.circuit-breaker.cooldown-seconds:300}")
    private long circuitBreakerCooldownSeconds = 300;

    @Value("${notifications.sms.reconciliation.sla-hours:24}")
    private long reconciliationSlaHours = 24;

    public InvoiceNotificationOutboxWorker(
            InvoiceNotificationOutboxRepository outboxRepository,
            TenantSmsQuotaRepository quotaRepository,
            SmsProvider smsProvider,
            AuditService auditService,
            SmsMetrics smsMetrics,
            TenantConnectionPreparer tenantConnectionPreparer,
            DataSource dataSource,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.quotaRepository = quotaRepository;
        this.smsProvider = smsProvider;
        this.auditService = auditService;
        this.smsMetrics = smsMetrics;
        this.tenantConnectionPreparer = tenantConnectionPreparer;
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.workerId = "sms-worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getWorkerId() {
        return workerId;
    }

    @Scheduled(fixedDelayString = "${notifications.sms.worker.interval-ms:2000}")
    public void processPendingBatch() {
        // 1. Recover stale IN_FLIGHT leases (>60s)
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int recovered = outboxRepository.releaseStaleLeases(Instant.now().minus(Duration.ofSeconds(60)));
                if (recovered > 0) {
                    log.info("[SMS-WORKER] Recovered {} stale IN_FLIGHT notification leases.", recovered);
                }
            });
        } catch (Exception e) {
            log.warn("[SMS-WORKER] Stale lease recovery warning: {}", e.getMessage());
        }

        // 2. Check circuit breaker state
        if (circuitBreakerOpen.get()) {
            if (circuitBreakerOpenedAt != null &&
                    Instant.now().isAfter(circuitBreakerOpenedAt.plusSeconds(circuitBreakerCooldownSeconds))) {
                log.info("[SMS-WORKER] Circuit breaker cooldown expired. Attempting half-open trial.");
                circuitBreakerOpen.set(false);
                circuitBreakerOpenedAt = null;
            } else {
                return;
            }
        }

        // 3. Claim pending batch
        List<InvoiceNotificationOutbox> batch;
        try {
            batch = outboxRepository.findPendingBatch(Instant.now(), PageRequest.of(0, 20));
        } catch (Exception e) {
            log.debug("[SMS-WORKER] findPendingBatch skipped: {}", e.getMessage());
            return;
        }
        if (batch.isEmpty()) {
            return;
        }

        for (InvoiceNotificationOutbox item : batch) {
            try {
                processSingleNotification(item);
            } catch (Exception ex) {
                log.error("[SMS-WORKER] Error processing outbox item {}: {}", item.getId(), ex.getMessage(), ex);
            }
        }
    }

    public void processSingleNotification(InvoiceNotificationOutbox item) {
        UUID tenantId = item.getTenantId();
        TenantContextHolder.setContext(TenantContext.create(tenantId, "sms-worker", Set.of("ROLE_SYSTEM_WORKER")));

        try {
            // Step 1: Claim lease and atomically consume quota within short DB transaction
            Boolean eligible = transactionTemplate.execute(status -> {
                Connection conn = DataSourceUtils.getConnection(dataSource);
                try {
                    tenantConnectionPreparer.prepareConnection(conn, tenantId);

                    int claimed = outboxRepository.claimLeaseAtomic(item.getId(), workerId, Instant.now());
                    if (claimed == 0) {
                        log.debug("[SMS-WORKER] Item {} already claimed by another worker.", item.getId());
                        return false;
                    }

                    var segResult = SmsSegmentationCalculator.calculate(item.getRenderedMessage());
                    int billingUnits = Math.max(1, segResult.commercialBillingUnits());

                    LocalDate today = LocalDate.now();
                    Instant now = Instant.now();
                    int consumed = quotaRepository.tryConsumeQuotaAtomic(tenantId, billingUnits, today, now);
                    if (consumed == 0) {
                        Optional<TenantSmsQuota> quotaOpt = quotaRepository.findById(tenantId);
                        if (quotaOpt.isEmpty()) {
                            quotaRepository.save(new TenantSmsQuota(tenantId, 1000));
                            consumed = quotaRepository.tryConsumeQuotaAtomic(tenantId, billingUnits, today, now);
                        } else if (quotaOpt.get().getResetDate().isBefore(today)) {
                            quotaRepository.resetQuotaIfNewDay(tenantId, today, now);
                            consumed = quotaRepository.tryConsumeQuotaAtomic(tenantId, billingUnits, today, now);
                        }
                    }

                    if (consumed == 0) {
                        log.warn("[SMS-WORKER] Tenant {} daily SMS quota exceeded. Deferring dispatch for item {}.", tenantId, item.getId());
                        outboxRepository.completeRetryScheduledAtomic(
                                item.getId(), workerId, computeBackoff(item.getAttemptCount()),
                                "QUOTA_EXCEEDED", "Tenant daily SMS quota limit reached",
                                FailureClassification.PROVIDER_CAPACITY_FAILURE
                        );
                        smsMetrics.recordFailed(smsProvider.getProviderName(), FailureClassification.PROVIDER_CAPACITY_FAILURE);
                        return false;
                    }
                    return true;
                } finally {
                    tenantConnectionPreparer.prepareConnection(conn, null);
                    DataSourceUtils.releaseConnection(conn, dataSource);
                }
            });

            if (eligible == null || !eligible) {
                return;
            }

            // Step 2: Dispatch to SMS provider OUTSIDE any DB transaction (zero DB connections held during HTTP call)
            long start = System.currentTimeMillis();
            SmsSendResult result;
            try {
                String senderId = smsProvider.supportsSenderId() ? "MoR-EIRS" : null;
                SmsSendRequest request = new SmsSendRequest(
                        item.getRecipientPhoneSnapshot(),
                        item.getRenderedMessage(),
                        senderId,
                        item.getCorrelationId()
                );
                result = smsProvider.sendTransactionalSms(request);
            } catch (Exception ex) {
                log.error("[SMS-WORKER] Outbound call exception on item {}: {}", item.getId(), ex.getMessage());
                result = SmsSendResult.unknown("DISPATCH_EXCEPTION", ex.getMessage());
            }
            long duration = System.currentTimeMillis() - start;
            smsMetrics.recordLatency(smsProvider.getProviderName(), duration);

            // Step 3: Record dispatch result in short DB transaction protected by CAS worker lease ownership
            final SmsSendResult finalResult = result;
            transactionTemplate.executeWithoutResult(status -> {
                Connection conn = DataSourceUtils.getConnection(dataSource);
                try {
                    tenantConnectionPreparer.prepareConnection(conn, tenantId);

                    if (finalResult.success()) {
                        int updated = outboxRepository.completeSubmissionAtomic(
                                item.getId(), workerId, finalResult.providerMessageId(), Instant.now()
                        );
                        if (updated == 0) {
                            log.warn("[SMS-WORKER] Lost lease ownership on item {}. Stale worker completion discarded.", item.getId());
                            return;
                        }
                        smsMetrics.recordSubmitted(smsProvider.getProviderName());

                        // PII-minimized immutable audit event (phone-number-free)
                        auditService.recordEvent(
                                tenantId, "SMS_NOTIFICATION", "SYSTEM",
                                AuditAction.SMS_PROVIDER_ACCEPTED.name(),
                                "INVOICE", item.getInvoiceId().toString(),
                                "outbox_id=" + item.getId() + ";provider=" + smsProvider.getProviderName() + ";msg_id=" + finalResult.providerMessageId(),
                                "127.0.0.1"
                        );
                    } else {
                        handleDispatchFailure(item, finalResult);
                    }
                } finally {
                    tenantConnectionPreparer.prepareConnection(conn, null);
                    DataSourceUtils.releaseConnection(conn, dataSource);
                }
            });

        } catch (Exception e) {
            log.error("[SMS-WORKER] Failed execution on item {}: {}", item.getId(), e.getMessage());
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    outboxRepository.completeRetryScheduledAtomic(
                            item.getId(), workerId, computeBackoff(item.getAttemptCount()),
                            "WORKER_EXCEPTION", e.getMessage(), FailureClassification.PROVIDER_NETWORK_FAILURE
                    );
                });
            } catch (Exception ex) {
                log.error("[SMS-WORKER] Could not record retry for item {}: {}", item.getId(), ex.getMessage());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void handleDispatchFailure(InvoiceNotificationOutbox item, SmsSendResult result) {
        FailureClassification classification = result.failureClassification() != null
                ? result.failureClassification()
                : FailureClassification.PROVIDER_NETWORK_FAILURE;

        switch (classification) {
            case MESSAGE_FAILURE -> {
                int updated = outboxRepository.completePermanentFailureAtomic(
                        item.getId(), workerId, result.errorCode(), result.errorMessage(), classification, Instant.now()
                );
                if (updated == 0) {
                    log.warn("[SMS-WORKER] Lost lease on item {}. Discarding stale completion.", item.getId());
                    return;
                }
                smsMetrics.recordFailed(smsProvider.getProviderName(), classification);
                auditService.recordEvent(
                        item.getTenantId(), "SMS_NOTIFICATION", "SYSTEM",
                        AuditAction.SMS_FAILED.name(),
                        "INVOICE", item.getInvoiceId().toString(),
                        "outbox_id=" + item.getId() + ";classification=MESSAGE_FAILURE;error=" + result.errorCode(),
                        "127.0.0.1"
                );
            }
            case PROVIDER_CONFIGURATION_FAILURE -> {
                log.error("[SMS-WORKER] Provider configuration failure (HTTP 401/403). Tripping circuit breaker.");
                circuitBreakerOpen.set(true);
                circuitBreakerOpenedAt = Instant.now();
                outboxRepository.releaseLeaseAtomic(item.getId(), workerId);
                smsMetrics.recordFailed(smsProvider.getProviderName(), classification);
            }
            case PROVIDER_CAPACITY_FAILURE -> {
                log.warn("[SMS-WORKER] Provider capacity exhausted (insufficient balance). Deferring records.");
                outboxRepository.releaseLeaseAtomic(item.getId(), workerId);
                smsMetrics.recordFailed(smsProvider.getProviderName(), classification);
            }
            case PROVIDER_NETWORK_FAILURE -> {
                Instant nextRetry = computeBackoff(item.getAttemptCount());
                int updated = outboxRepository.completeRetryScheduledAtomic(
                        item.getId(), workerId, nextRetry, result.errorCode(), result.errorMessage(), classification
                );
                if (updated == 0) {
                    log.warn("[SMS-WORKER] Lost lease on item {}. Discarding stale completion.", item.getId());
                    return;
                }
                smsMetrics.recordRetry(smsProvider.getProviderName());
                auditService.recordEvent(
                        item.getTenantId(), "SMS_NOTIFICATION", "SYSTEM",
                        AuditAction.SMS_RETRY_SCHEDULED.name(),
                        "INVOICE", item.getInvoiceId().toString(),
                        "outbox_id=" + item.getId() + ";attempt=" + (item.getAttemptCount() + 1) + ";next_retry=" + nextRetry,
                        "127.0.0.1"
                );
            }
            case PROVIDER_UNKNOWN_OUTCOME -> {
                int updated = outboxRepository.completeSubmissionUnknownAtomic(
                        item.getId(), workerId, result.errorCode(), result.errorMessage()
                );
                if (updated == 0) {
                    log.warn("[SMS-WORKER] Lost lease on item {}. Discarding stale completion.", item.getId());
                    return;
                }
                smsMetrics.recordUnknownOutcome(smsProvider.getProviderName());
                auditService.recordEvent(
                        item.getTenantId(), "SMS_NOTIFICATION", "SYSTEM",
                        AuditAction.SMS_SUBMISSION_UNKNOWN.name(),
                        "INVOICE", item.getInvoiceId().toString(),
                        "outbox_id=" + item.getId() + ";reason=" + result.errorMessage(),
                        "127.0.0.1"
                );
            }
        }
    }

    @Scheduled(fixedDelayString = "${notifications.sms.reconciliation.interval-ms:30000}")
    public void reconcileUnknownSubmissions() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                List<InvoiceNotificationOutbox> unknownList = outboxRepository.findUnknownSubmissions(Instant.now(), PageRequest.of(0, 10));
                if (unknownList.isEmpty()) {
                    return;
                }

                for (InvoiceNotificationOutbox item : unknownList) {
                    item.markReconciling();
                    outboxRepository.save(item);

                    // 1. If provider supports active status lookup, poll delivery status
                    if (smsProvider.supportsStatusLookup() && item.getProviderMessageId() != null) {
                        Optional<SmsDeliveryStatus> statusOpt = smsProvider.getDeliveryStatus(item.getProviderMessageId());
                        if (statusOpt.isPresent()) {
                            SmsDeliveryStatus deliveryStatus = statusOpt.get();
                            if (deliveryStatus.delivered()) {
                                item.markDelivered();
                                smsMetrics.recordDelivered(smsProvider.getProviderName());
                            } else {
                                item.markDeliveryFailed(deliveryStatus.statusDescription());
                            }
                            outboxRepository.save(item);
                            continue;
                        }
                    }

                    // 2. Controlled SUBMISSION_UNKNOWN_FINAL after configured SLA window
                    // Explicitly indicates: Delivery outcome could not be established (NOT confirmed failure)
                    Instant cutoff = Instant.now().minus(Duration.ofHours(reconciliationSlaHours));
                    if (item.getCreatedAt().isBefore(cutoff) || !smsProvider.supportsStatusLookup()) {
                        log.info("[SMS-RECONCILIATION] Transitioning outbox item {} to SUBMISSION_UNKNOWN_FINAL after {}h window (or status lookup unsupported). Delivery outcome could not be established.",
                                item.getId(), reconciliationSlaHours);
                        item.markSubmissionUnknownFinal();
                        outboxRepository.save(item);
                    } else {
                        item.releaseLock();
                        outboxRepository.save(item);
                    }
                }
            });
        } catch (Exception e) {
            log.debug("[SMS-RECONCILIATION] reconcileUnknownSubmissions skipped: {}", e.getMessage());
        }
    }

    private Instant computeBackoff(int attemptCount) {
        long delaySeconds = switch (attemptCount) {
            case 1 -> 30;
            case 2 -> 120;
            case 3 -> 600;
            case 4 -> 1800;
            default -> 3600;
        };
        return Instant.now().plusSeconds(delaySeconds);
    }
}
