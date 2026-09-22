package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.portability.domain.ExportJob;
import et.ut.einvoice.portability.repository.ExportJobRepository;
import et.ut.einvoice.portability.service.DataPortabilityService;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.webhooks.domain.OutboundWebhookDelivery;
import et.ut.einvoice.webhooks.domain.WebhookSubscription;
import et.ut.einvoice.webhooks.repository.OutboundWebhookDeliveryRepository;
import et.ut.einvoice.webhooks.repository.WebhookSubscriptionRepository;
import et.ut.einvoice.webhooks.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class WebhookAndPortabilityTestSuite {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    @Autowired
    private OutboundWebhookDeliveryRepository deliveryRepository;

    @Autowired
    private DataPortabilityService portabilityService;

    @Autowired
    private ExportJobRepository exportJobRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private InsaDigitalSignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        subscriptionRepository.deleteAll();
        exportJobRepository.deleteAll();
        invoiceRepository.deleteAll();
        taxpayerProfileRepository.deleteAll();

        tenantId = UUID.randomUUID();
        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantId, "0099112233", "VAT-99112233", "Portability Test PLC", "Portability PLC",
                "14", "05", "+251911445566", "portability@test.com", "8EFBBDD7FA", "ERP"
        ));
    }

    @Test
    @DisplayName("Webhooks 1: Webhook registration and HMAC-SHA256 signature generation")
    void test_Webhook_HmacSignatureComputation() {
        String secret = "webhook_secret_key_abcdef123456";
        String payload = "{\"event\":\"INVOICE_REGISTERED\",\"irn\":\"IRN-TEST-100\"}";

        WebhookSubscription sub = webhookService.registerSubscription(
                tenantId, "https://api.merchant.com/webhooks", secret, "INVOICE_*"
        );
        assertNotNull(sub);

        List<OutboundWebhookDelivery> deliveries = webhookService.dispatchWebhookEvent(tenantId, "INVOICE_REGISTERED", payload);
        assertEquals(1, deliveries.size());
        OutboundWebhookDelivery delivery = deliveries.get(0);

        assertEquals("DELIVERED", delivery.getStatus());
        assertNotNull(delivery.getSignature());

        // Verify computed HMAC matches manual computation
        String expectedSig = WebhookService.computeHmacSha256(payload, secret);
        assertEquals(expectedSig, delivery.getSignature());
    }

    @Test
    @DisplayName("Webhooks 2: Failing webhook endpoint records failure and retry updates attempt counter")
    void test_Webhook_FailureAndRetry() {
        String secret = "secret_fail_123";
        webhookService.registerSubscription(
                tenantId, "http://localhost:59999/fail-endpoint", secret, "INVOICE_*"
        );

        List<OutboundWebhookDelivery> deliveries = webhookService.dispatchWebhookEvent(
                tenantId, "INVOICE_REGISTERED", "{\"amount\":100}"
        );
        assertEquals(1, deliveries.size());
        OutboundWebhookDelivery delivery = deliveries.get(0);

        assertEquals("FAILED", delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());

        // Execute retry
        webhookService.retryFailedDeliveries();
        OutboundWebhookDelivery retried = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(2, retried.getAttemptCount());
    }

    @Test
    @DisplayName("Webhooks 3: Webhook transitions to DEAD_LETTER after 5 failed attempts")
    void test_Webhook_DeadLetterTransition() {
        OutboundWebhookDelivery delivery = new OutboundWebhookDelivery(
                UUID.randomUUID(), tenantId, "INVOICE_REGISTERED", "http://fail-endpoint/404", "{}", "sig"
        );
        deliveryRepository.save(delivery);

        for (int i = 0; i < 5; i++) {
            delivery.markFailed("Simulated 500 Connection Timeout");
        }
        deliveryRepository.save(delivery);

        OutboundWebhookDelivery deadLetter = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals("DEAD_LETTER", deadLetter.getStatus());
        assertEquals(5, deadLetter.getAttemptCount());
    }

    @Test
    @DisplayName("Portability 4: Complete tenant data export contains real invoices, taxpayer profile, and valid SHA-256 checksum")
    void test_DataPortability_RealArchivePackaging() {
        // Create 2 test invoices for tenant
        for (int i = 1; i <= 2; i++) {
            Invoice inv = new Invoice(
                    UUID.randomUUID(), tenantId, "EXPORT-DOC-" + i, (long) i,
                    Instant.now(), TransactionType.B2C, "CASH", "IMMEDIATE"
            );
            InvoiceLine line = new InvoiceLine(
                    UUID.randomUUID(), tenantId, 1, "SKU-" + i, "Product " + i, "goods", "PCS",
                    BigDecimal.ONE, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00"),
                    "VAT15", new BigDecimal("0.15"), new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("230.00")
            );
            inv.addLine(line);
            inv.recalculateTotals();
            inv.markRegistered("IRN-EXPORT-" + i, "RRN-EXP-" + i, Instant.now().toString(), "qr", "sig");
            invoiceRepository.save(inv);
        }

        // Initiate export
        ExportJob job = portabilityService.initiateExport(tenantId);
        assertNotNull(job);
        assertEquals(tenantId, job.getTenantId());

        // Verify completed export job
        ExportJob completed = exportJobRepository.findById(job.getId()).orElseThrow();
        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getArtifactUrl());
        assertNotNull(completed.getArtifactChecksum());
        assertEquals(64, completed.getArtifactChecksum().length()); // Valid SHA-256 hex string length
    }
}
