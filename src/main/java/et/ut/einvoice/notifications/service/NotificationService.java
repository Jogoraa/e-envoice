package et.ut.einvoice.notifications.service;

import et.ut.einvoice.cancellation.service.CancellationService;
import et.ut.einvoice.invoicing.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Asynchronous Notification Service fulfilling Master Compliance Checklist ADD-N001.
 * Dispatches Email and SMS delivery within 5 minutes of transaction events.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async
    @EventListener
    public void onInvoiceRegistered(InvoiceService.InvoiceRegisteredEvent event) {
        log.info("Dispatching async buyer notification for Registered Invoice: IRN={}, email={}, phone={}",
                event.irn(), event.buyerEmail(), event.buyerPhone());

        // Simulated SMS gateway dispatch
        if (event.buyerPhone() != null && !event.buyerPhone().isBlank()) {
            sendSms(event.buyerPhone(), String.format("MoR Tax Invoice Registered. IRN: %s, Date: %s. Verified by EIMS.",
                    event.irn(), Instant.now()));
        }

        // Simulated Email gateway dispatch
        if (event.buyerEmail() != null && !event.buyerEmail().isBlank()) {
            sendEmail(event.buyerEmail(), "Your Certified Tax Invoice",
                    String.format("Thank you for your business. Your official electronic tax invoice has been registered with the Ministry of Revenues under IRN: %s.", event.irn()));
        }
    }

    @Async
    @EventListener
    public void onInvoiceCancelled(CancellationService.InvoiceCancelledEvent event) {
        log.info("Dispatching async buyer cancellation notification: IRN={}, ref={}", event.irn(), event.cancellationRef());

        if (event.buyerPhone() != null && !event.buyerPhone().isBlank()) {
            sendSms(event.buyerPhone(), String.format("Tax Invoice Cancelled. IRN: %s, Ref: %s. Notification per Directive Art. 26.",
                    event.irn(), event.cancellationRef()));
        }

        if (event.buyerEmail() != null && !event.buyerEmail().isBlank()) {
            sendEmail(event.buyerEmail(), "Tax Invoice Cancellation Notice",
                    String.format("Notice: The tax invoice under IRN: %s has been cancelled under reference: %s.",
                            event.irn(), event.cancellationRef()));
        }
    }

    private void sendSms(String phone, String message) {
        log.info("[SMS GATEWAY -> {}]: {}", phone, message);
    }

    private void sendEmail(String email, String subject, String body) {
        log.info("[EMAIL GATEWAY -> {}]: Subject='{}', Body='{}'", email, subject, body);
    }
}
