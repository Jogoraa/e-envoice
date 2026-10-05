package et.ut.einvoice.compliance.service;

import et.ut.einvoice.compliance.domain.ExemptSectorAuthorization;
import et.ut.einvoice.compliance.domain.ExemptSectorReportLine;
import et.ut.einvoice.compliance.domain.ExemptSectorSummaryReport;
import et.ut.einvoice.compliance.repository.ExemptSectorAuthorizationRepository;
import et.ut.einvoice.compliance.repository.ExemptSectorReportLineRepository;
import et.ut.einvoice.compliance.repository.ExemptSectorSummaryReportRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Implements high-volume exempt-sector periodic summary reporting requirements
 * under Directive No. 1142/2026 Art. 20.
 *
 * Art. 20 rules enforced:
 *   1. B2C consumer transactions only can be reported in summary without direct EIRS connection.
 *   2. B2B transactions MUST be registered directly with EIRS and are strictly excluded from summaries (Art. 20(5)).
 *   3. Aggregated report calculates mandatory lines: service/goods type, quantity, unit price, total price,
 *      tax type, tax rate, tax amount, and grand total (Art. 20(3)(c)).
 *   4. Freezes reports upon submission with SHA-256 cryptographic checksum.
 */
@Service
public class ExemptSectorReportingService {

    private static final Logger log = LoggerFactory.getLogger(ExemptSectorReportingService.class);

    private final ExemptSectorAuthorizationRepository authorizationRepository;
    private final ExemptSectorSummaryReportRepository summaryReportRepository;
    private final ExemptSectorReportLineRepository reportLineRepository;
    private final InvoiceRepository invoiceRepository;

    public ExemptSectorReportingService(ExemptSectorAuthorizationRepository authorizationRepository,
                                        ExemptSectorSummaryReportRepository summaryReportRepository,
                                        ExemptSectorReportLineRepository reportLineRepository,
                                        InvoiceRepository invoiceRepository) {
        this.authorizationRepository = authorizationRepository;
        this.summaryReportRepository = summaryReportRepository;
        this.reportLineRepository = reportLineRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public ExemptSectorAuthorization grantAuthorization(UUID tenantId, String sectorCode,
                                                         String authorizedBy, String authorizationReference,
                                                         String reportingFrequency, Instant effectiveFrom,
                                                         Instant effectiveTo) {
        ExemptSectorAuthorization auth = authorizationRepository.findByTenantId(tenantId)
                .orElse(new ExemptSectorAuthorization());

        auth.setId(auth.getId() != null ? auth.getId() : UUID.randomUUID());
        auth.setTenantId(tenantId);
        auth.setSectorCode(sectorCode);
        auth.setAuthorizedBy(authorizedBy);
        auth.setAuthorizationReference(authorizationReference);
        auth.setReportingFrequency(reportingFrequency != null ? reportingFrequency : "DAILY");
        auth.setStatus("ACTIVE");
        auth.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : Instant.now());
        auth.setEffectiveTo(effectiveTo);

        return authorizationRepository.save(auth);
    }

    @Transactional(readOnly = true)
    public Optional<ExemptSectorAuthorization> getAuthorization(UUID tenantId) {
        return authorizationRepository.findByTenantId(tenantId);
    }

    @Transactional
    public ExemptSectorSummaryReport generateDraftSummaryReport(UUID tenantId, String reportPeriodLabel,
                                                               Instant periodFrom, Instant periodTo) {
        ExemptSectorAuthorization auth = authorizationRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")
                .orElseThrow(() -> new IllegalStateException("Tenant does not have an ACTIVE exempt-sector authorization under Art. 20"));

        summaryReportRepository.findByTenantIdAndReportPeriodLabelAndReportingFrequency(
                tenantId, reportPeriodLabel, auth.getReportingFrequency()
        ).ifPresent(existing -> {
            if ("SUBMITTED".equals(existing.getStatus()) || "ACCEPTED".equals(existing.getStatus())) {
                throw new IllegalStateException("Report for period " + reportPeriodLabel + " is already " + existing.getStatus() + " and cannot be regenerated");
            }
            summaryReportRepository.delete(existing);
        });

        ExemptSectorSummaryReport report = new ExemptSectorSummaryReport(
                UUID.randomUUID(), tenantId, auth.getId(),
                reportPeriodLabel, auth.getReportingFrequency(),
                periodFrom, periodTo
        );

        List<Invoice> invoices = invoiceRepository.findByTenantIdAndInvoiceDateBetween(tenantId, periodFrom, periodTo);

        // Art. 20(5): Filter out B2B transactions; only B2C transactions belong in exempt-sector periodic summaries
        List<Invoice> b2cInvoices = invoices.stream()
                .filter(inv -> inv.getTransactionType() != et.ut.einvoice.invoicing.domain.TransactionType.B2B)
                .filter(inv -> inv.getBuyerTin() == null || inv.getBuyerTin().isBlank())
                .toList();

        Map<String, AggregatedLineData> aggregated = new HashMap<>();

        for (Invoice inv : b2cInvoices) {
            String key = "B2C_GENERAL_SERVICES";
            AggregatedLineData agg = aggregated.computeIfAbsent(key, k -> new AggregatedLineData(
                    "services", "Consumer Retail Services / Goods", "TXN",
                    "VAT15", new BigDecimal("0.1500")
            ));
            agg.addInvoice(inv.getPreTaxTotal(), inv.getTaxTotal(), inv.getGrandTotal());
        }

        List<ExemptSectorReportLine> lines = new ArrayList<>();
        int lineNumber = 1;

        if (aggregated.isEmpty()) {
            ExemptSectorReportLine defaultLine = new ExemptSectorReportLine(
                    UUID.randomUUID(), report.getId(), tenantId, 1,
                    "services", "Standard Consumer Services",
                    BigDecimal.ZERO, "TXN", BigDecimal.ZERO, BigDecimal.ZERO,
                    "VAT15", new BigDecimal("0.1500"), BigDecimal.ZERO, BigDecimal.ZERO, 0L
            );
            lines.add(defaultLine);
        } else {
            for (AggregatedLineData agg : aggregated.values()) {
                BigDecimal qty = BigDecimal.valueOf(agg.invoiceCount);
                BigDecimal unitPrice = agg.invoiceCount > 0
                        ? agg.totalPrice.divide(qty, 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                ExemptSectorReportLine line = new ExemptSectorReportLine(
                        UUID.randomUUID(), report.getId(), tenantId, lineNumber++,
                        agg.natureOfSupplies, agg.description,
                        qty, agg.unit, unitPrice, agg.totalPrice,
                        agg.taxCode, agg.taxRate, agg.taxAmount, agg.grandTotal, agg.invoiceCount
                );
                lines.add(line);
            }
        }

        report.setLines(lines);
        report.recalculateTotals();

        ExemptSectorSummaryReport savedReport = summaryReportRepository.save(report);
        for (ExemptSectorReportLine l : lines) {
            reportLineRepository.save(l);
        }

        return savedReport;
    }

    @Transactional
    public ExemptSectorSummaryReport submitSummaryReport(UUID reportId, String submittedBy) {
        ExemptSectorSummaryReport report = summaryReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (!"DRAFT".equals(report.getStatus())) {
            throw new IllegalStateException("Report cannot be submitted because status is: " + report.getStatus());
        }

        List<ExemptSectorReportLine> lines = reportLineRepository.findByReportIdOrderByLineNumberAsc(reportId);
        report.setLines(lines);
        report.recalculateTotals();

        String payload = report.getId() + ":" + report.getTenantId() + ":" + report.getReportPeriodLabel() +
                ":" + report.getTotalGrandTotal() + ":" + report.getTotalInvoiceCount();
        String sha256 = calculateSha256(payload);

        report.submit(submittedBy, sha256);
        return summaryReportRepository.save(report);
    }

    @Transactional
    public ExemptSectorSummaryReport reviewSummaryReport(UUID reportId, boolean accept, String reason) {
        ExemptSectorSummaryReport report = summaryReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (!"SUBMITTED".equals(report.getStatus())) {
            throw new IllegalStateException("Only SUBMITTED reports can be reviewed. Current: " + report.getStatus());
        }

        if (accept) {
            report.accept();
        } else {
            report.reject(reason != null ? reason : "Report rejected by Authority");
        }
        return summaryReportRepository.save(report);
    }

    private String calculateSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    private static class AggregatedLineData {
        String natureOfSupplies;
        String description;
        String unit;
        String taxCode;
        BigDecimal taxRate;
        BigDecimal totalPrice = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        long invoiceCount = 0;

        AggregatedLineData(String natureOfSupplies, String description, String unit, String taxCode, BigDecimal taxRate) {
            this.natureOfSupplies = natureOfSupplies;
            this.description = description;
            this.unit = unit;
            this.taxCode = taxCode;
            this.taxRate = taxRate;
        }

        void addInvoice(BigDecimal sub, BigDecimal tax, BigDecimal grand) {
            if (sub != null) this.totalPrice = this.totalPrice.add(sub);
            if (tax != null) this.taxAmount = this.taxAmount.add(tax);
            if (grand != null) this.grandTotal = this.grandTotal.add(grand);
            this.invoiceCount++;
        }
    }
}
