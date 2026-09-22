package et.ut.einvoice.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.customer.domain.Customer;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.notifications.domain.InvoiceNotificationOutbox;
import et.ut.einvoice.notifications.domain.NotificationType;
import et.ut.einvoice.notifications.repository.NotificationOptOutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates whether an invoice event warrants transactional SMS notification
 * based on recipient phone validity, customer opt-out preferences, and template policies.
 */
@Service
public class InvoiceNotificationPolicyService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNotificationPolicyService.class);

    private final CustomerRepository customerRepository;
    private final NotificationOptOutRepository optOutRepository;
    private final InvoiceNotificationTemplateService templateService;
    private final ObjectMapper objectMapper;

    public InvoiceNotificationPolicyService(
            CustomerRepository customerRepository,
            NotificationOptOutRepository optOutRepository,
            InvoiceNotificationTemplateService templateService,
            ObjectMapper objectMapper
    ) {
        this.customerRepository = customerRepository;
        this.optOutRepository = optOutRepository;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    public record PolicyDecision(
            boolean shouldNotify,
            String rejectionReason,
            InvoiceNotificationOutbox outboxRecord
    ) {
        public static PolicyDecision reject(String reason) {
            return new PolicyDecision(false, reason, null);
        }

        public static PolicyDecision accept(InvoiceNotificationOutbox outbox) {
            return new PolicyDecision(true, null, outbox);
        }
    }

    /**
     * Evaluates policy and prepares a durable outbox record for invoice registration.
     */
    public PolicyDecision evaluateRegistrationNotification(
            Invoice invoice,
            String sellerName,
            String correlationId
    ) {
        String rawPhone = invoice.getBuyerPhone();
        if (rawPhone == null || rawPhone.isBlank()) {
            return PolicyDecision.reject("BUYER_PHONE_EMPTY");
        }

        String canonicalPhone;
        try {
            canonicalPhone = EthiopianPhoneNormalizer.normalize(rawPhone);
        } catch (Exception e) {
            log.warn("Invoice {}: buyer phone '{}' rejected by normalizer: {}", invoice.getId(), rawPhone, e.getMessage());
            return PolicyDecision.reject("INVALID_PHONE_NUMBER: " + e.getMessage());
        }

        UUID tenantId = invoice.getTenantId();

        // 1. Check Walk-in Opt-Out table
        if (optOutRepository.existsByTenantIdAndPhoneAndOptOutType(tenantId, canonicalPhone, "TRANSACTIONAL")) {
            log.info("Invoice {}: recipient {} opted out of transactional notifications.", invoice.getId(), canonicalPhone);
            return PolicyDecision.reject("CUSTOMER_OPTED_OUT");
        }

        // 2. Check Customer Master if known
        String preferredLanguage = "am";
        String recipientPartyId = invoice.getBuyerTin() != null && !invoice.getBuyerTin().isBlank()
                ? invoice.getBuyerTin()
                : canonicalPhone;

        if (invoice.getBuyerTin() != null) {
            Optional<Customer> customerOpt = customerRepository.findByTenantIdAndTin(tenantId, invoice.getBuyerTin());
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                recipientPartyId = customer.getId().toString();
                if (customer.getPreferredLanguage() != null && !customer.getPreferredLanguage().isBlank()) {
                    preferredLanguage = customer.getPreferredLanguage();
                }
            }
        }

        // 3. Render approved notification template
        var rendered = templateService.renderRegistrationNotification(
                invoice.getDocumentNumber(),
                sellerName,
                invoice.getGrandTotal(),
                invoice.getIrn(),
                invoice.getPublicVerificationToken(),
                preferredLanguage
        );

        // 4. Compute deterministic idempotency key (party-based, snapshot phone)
        String idempotencyKey = InvoiceNotificationTemplateService.sha256Hex(
                tenantId + ":" + invoice.getId() + ":" + NotificationType.INVOICE_REGISTERED + ":" + recipientPartyId
        );

        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(rendered.parameters());
        } catch (JsonProcessingException e) {
            paramsJson = "{}";
        }

        InvoiceNotificationOutbox outbox = new InvoiceNotificationOutbox(
                UUID.randomUUID(),
                tenantId,
                invoice.getId(),
                recipientPartyId,
                canonicalPhone,
                NotificationType.INVOICE_REGISTERED,
                rendered.templateId(),
                rendered.templateVersion(),
                paramsJson,
                rendered.messageText(),
                rendered.messageHash(),
                idempotencyKey,
                "MOCK",
                correlationId
        );

        return PolicyDecision.accept(outbox);
    }

    /**
     * Evaluates policy and prepares a durable outbox record for invoice cancellation.
     */
    public PolicyDecision evaluateCancellationNotification(
            Invoice invoice,
            String cancellationRef,
            String correlationId
    ) {
        String rawPhone = invoice.getBuyerPhone();
        if (rawPhone == null || rawPhone.isBlank()) {
            return PolicyDecision.reject("BUYER_PHONE_EMPTY");
        }

        String canonicalPhone;
        try {
            canonicalPhone = EthiopianPhoneNormalizer.normalize(rawPhone);
        } catch (Exception e) {
            return PolicyDecision.reject("INVALID_PHONE_NUMBER: " + e.getMessage());
        }

        UUID tenantId = invoice.getTenantId();
        String recipientPartyId = invoice.getBuyerTin() != null && !invoice.getBuyerTin().isBlank()
                ? invoice.getBuyerTin()
                : canonicalPhone;

        var rendered = templateService.renderCancellationNotification(
                invoice.getDocumentNumber(),
                invoice.getIrn(),
                cancellationRef,
                invoice.getPublicVerificationToken(),
                "am"
        );

        String idempotencyKey = InvoiceNotificationTemplateService.sha256Hex(
                tenantId + ":" + invoice.getId() + ":" + NotificationType.INVOICE_CANCELLED + ":" + recipientPartyId
        );

        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(rendered.parameters());
        } catch (JsonProcessingException e) {
            paramsJson = "{}";
        }

        InvoiceNotificationOutbox outbox = new InvoiceNotificationOutbox(
                UUID.randomUUID(),
                tenantId,
                invoice.getId(),
                recipientPartyId,
                canonicalPhone,
                NotificationType.INVOICE_CANCELLED,
                rendered.templateId(),
                rendered.templateVersion(),
                paramsJson,
                rendered.messageText(),
                rendered.messageHash(),
                idempotencyKey,
                "MOCK",
                correlationId
        );

        return PolicyDecision.accept(outbox);
    }
}
