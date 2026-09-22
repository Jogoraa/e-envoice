package et.ut.einvoice.notifications.metrics;

import et.ut.einvoice.notifications.domain.FailureClassification;
import et.ut.einvoice.notifications.domain.NotificationStatus;
import et.ut.einvoice.notifications.domain.NotificationType;
import et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Production low-cardinality Micrometer metrics for SMS notifications.
 * Strictly avoids high-cardinality tenant labels to ensure scale up to 1,000,000 tenants.
 */
@Component
public class SmsMetrics {

    private final MeterRegistry registry;

    public SmsMetrics(MeterRegistry registry, InvoiceNotificationOutboxRepository outboxRepository) {
        this.registry = registry;

        // Queue depth gauges per status
        for (NotificationStatus status : NotificationStatus.values()) {
            Gauge.builder("sms_queue_depth", outboxRepository, repo -> repo.countByStatus(status))
                    .tag("status", status.name())
                    .description("Current number of SMS notifications in outbox by status")
                    .register(registry);
        }
    }

    public void recordCreated(NotificationType type) {
        Counter.builder("sms_notifications_created_total")
                .tag("notification_type", type != null ? type.name() : "UNKNOWN")
                .description("Total number of SMS notifications enqueued in outbox")
                .register(registry)
                .increment();
    }

    public void recordSubmitted(String provider) {
        Counter.builder("sms_notifications_submitted_total")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .description("Total number of SMS notifications accepted by provider")
                .register(registry)
                .increment();
    }

    public void recordDelivered(String provider) {
        Counter.builder("sms_notifications_delivered_total")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .description("Total number of SMS notifications confirmed delivered to handset")
                .register(registry)
                .increment();
    }

    public void recordFailed(String provider, FailureClassification failureClass) {
        Counter.builder("sms_notifications_failed_total")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .tag("failure_class", failureClass != null ? failureClass.name() : "UNKNOWN")
                .description("Total number of failed SMS notifications by failure classification")
                .register(registry)
                .increment();
    }

    public void recordUnknownOutcome(String provider) {
        Counter.builder("sms_notifications_unknown_total")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .description("Total number of SMS submissions with unknown outcome")
                .register(registry)
                .increment();
    }

    public void recordRetry(String provider) {
        Counter.builder("sms_notifications_retry_total")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .description("Total number of SMS notification retries scheduled")
                .register(registry)
                .increment();
    }

    public void recordLatency(String provider, long durationMillis) {
        Timer.builder("sms_provider_latency_seconds")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .description("SMS provider HTTP dispatch roundtrip latency")
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
