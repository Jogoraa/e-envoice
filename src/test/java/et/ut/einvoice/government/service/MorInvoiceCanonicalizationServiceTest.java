package et.ut.einvoice.government.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.documents.service.QrCodeService;
import et.ut.einvoice.government.domain.MorReceiptViewModel;
import et.ut.einvoice.government.infrastructure.mor.dto.MorRegisterPayload;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MorInvoiceCanonicalizationServiceTest {

    private MorInvoiceCanonicalizationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new MorInvoiceCanonicalizationService(objectMapper, new QrCodeService());
    }

    @Test
    @DisplayName("numberToWords handles zero, decimals, boundaries, and deterministic output")
    void testNumberToWords() {
        assertEquals("Zero Birr and 00/100", service.numberToWords(BigDecimal.ZERO));
        assertEquals("Zero Birr and 00/100", service.numberToWords(null));
        assertEquals("Zero Birr and 75/100", service.numberToWords(new BigDecimal("0.75")));
        assertEquals("One Thousand Forty Two Birr and 50/100", service.numberToWords(new BigDecimal("1042.50")));
        assertEquals("Twenty One Thousand Two Hundred Seventy Five Birr and 00/100", service.numberToWords(new BigDecimal("21275.00")));
        assertEquals("One Million Birr and 00/100", service.numberToWords(new BigDecimal("1000000.00")));
    }

    @Test
    @DisplayName("buildCanonicalQrData contains all MoR required keys and formats")
    void testBuildCanonicalQrData() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TaxpayerProfile seller = new TaxpayerProfile(
                tenantId, "0011223344", "VAT-11223344", "Seller PLC", "Seller Trade",
                "14", "01", "+251911000111", "seller@example.com", "8EFBBDD7FA", "ERP"
        );

        Invoice invoice = new Invoice(
                UUID.randomUUID(), tenantId, "DOC-2026-001", 101L, Instant.now(),
                TransactionType.B2B, "CASH", "IMMEDIATE"
        );
        invoice.setBuyerTin("0099887766");
        invoice.setBuyerLegalName("Buyer Corp");

        InvoiceLine line1 = new InvoiceLine(
                UUID.randomUUID(), tenantId, 1, "ITM-01", "Item 1", "goods", "PCS",
                new BigDecimal("2"), new BigDecimal("5000.00"), BigDecimal.ZERO,
                new BigDecimal("10000.00"), "VAT15", new BigDecimal("0.15"),
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("11500.00")
        );

        InvoiceLine line2 = new InvoiceLine(
                UUID.randomUUID(), tenantId, 2, "ITM-02", "Item 2", "goods", "PCS",
                new BigDecimal("1"), new BigDecimal("8500.00"), BigDecimal.ZERO,
                new BigDecimal("8500.00"), "VAT15", new BigDecimal("0.15"),
                new BigDecimal("1275.00"), BigDecimal.ZERO, new BigDecimal("9775.00")
        );

        invoice.addLine(line1);
        invoice.addLine(line2);
        invoice.recalculateTotals();
        invoice.markRegistered("TEST-IRN-998877", "RRN-1", "2026-09-19", "QR", "SIGNATURE_SIG_123");

        String qrJson = service.buildCanonicalQrData(invoice, seller, "SIGNATURE_SIG_123", "2026-09-19 12:00:00.000");
        assertNotNull(qrJson);

        JsonNode node = objectMapper.readTree(qrJson);
        assertEquals("0011223344", node.get("Seller TIN").asText());
        assertEquals("0099887766", node.get("Buyer TIN").asText());
        assertEquals("VAT-11223344", node.get("Seller VAT No.").asText());
        assertEquals("DOC-2026-001", node.get("Seller Invoice No").asText());
        assertEquals("TEST-IRN-998877", node.get("IRN").asText());
        assertEquals("SIGNATURE_SIG_123", node.get("Signature").asText());
        assertEquals("2", node.get("Number of Items").asText());
        assertEquals("1", node.get("Serial No. of Item with Higest Taxable Value").asText());
    }

    @Test
    @DisplayName("buildGovernmentPayload adheres to MoR ISO date and immediate payment term")
    void testBuildGovernmentPayload() {
        UUID tenantId = UUID.randomUUID();
        TaxpayerProfile seller = new TaxpayerProfile(
                tenantId, "0011223344", "VAT-11223344", "Seller PLC", "Seller Trade",
                "14", "01", "+251911000111", "seller@example.com", "8EFBBDD7FA", "ERP"
        );

        Invoice invoice = new Invoice(
                UUID.randomUUID(), tenantId, "DOC-2026-001", 101L, Instant.now(),
                TransactionType.B2C, "CASH", "IMMEDIATE"
        );

        InvoiceLine line = new InvoiceLine(
                UUID.randomUUID(), tenantId, 1, "ITM-01", "Coffee", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), "VAT15", new BigDecimal("0.15"),
                new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("115.00")
        );
        invoice.addLine(line);
        invoice.recalculateTotals();

        MorRegisterPayload payload = service.buildGovernmentPayload(invoice, seller, "8EFBBDD7FA", "ERP");
        assertNotNull(payload);
        assertEquals("IMMIDIATE", payload.paymentDetails().paymentTerm());
        assertEquals("8EFBBDD7FA", payload.sourceSystem().systemNumber());
        assertEquals("ERP", payload.sourceSystem().systemType());
        assertNotNull(payload.documentDetails().date());
        assertTrue(payload.documentDetails().date().contains("T"));
    }

    @Test
    @DisplayName("buildReceiptViewModel creates complete bilingual view model")
    void testBuildReceiptViewModel() {
        UUID tenantId = UUID.randomUUID();
        TaxpayerProfile seller = new TaxpayerProfile(
                tenantId, "0011223344", "VAT-11223344", "Seller PLC", "Seller Trade",
                "14", "01", "+251911000111", "seller@example.com", "8EFBBDD7FA", "ERP"
        );

        Invoice invoice = new Invoice(
                UUID.randomUUID(), tenantId, "DOC-2026-001", 101L, Instant.now(),
                TransactionType.B2B, "CASH", "IMMEDIATE"
        );

        InvoiceLine line = new InvoiceLine(
                UUID.randomUUID(), tenantId, 1, "ITM-01", "Item", "goods", "PCS",
                BigDecimal.ONE, new BigDecimal("18500.00"), BigDecimal.ZERO,
                new BigDecimal("18500.00"), "VAT15", new BigDecimal("0.15"),
                new BigDecimal("2775.00"), BigDecimal.ZERO, new BigDecimal("21275.00")
        );
        invoice.addLine(line);
        invoice.recalculateTotals();

        MorReceiptViewModel vm = service.buildReceiptViewModel(invoice, seller, "BASE64_QR", "{}");
        assertNotNull(vm);
        assertEquals("DOC-2026-001", vm.invoiceNumber());
        assertTrue(vm.transactionTypeBadgeAmharic().contains("TAX INVOICE"));
        assertTrue(vm.inWordsBirr().contains("Twenty One Thousand Two Hundred Seventy Five Birr and 00/100"));
    }
}
