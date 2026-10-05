package et.ut.einvoice.invoicing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.invoicing.domain.ManualFiscalDocument;
import et.ut.einvoice.invoicing.domain.ManualFiscalState;
import et.ut.einvoice.invoicing.dto.*;
import et.ut.einvoice.invoicing.repository.ManualFiscalDocumentRepository;
import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service managing Manual Paper/QR Fallback Invoices and Post-Outage Reconciliation.
 * Implements FDRE MoR Directive No. 1142/2026 Art. 22.
 */
@Service
public class ManualInvoiceReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ManualInvoiceReconciliationService.class);
    private static final Duration STATUTORY_RECONCILIATION_WINDOW = Duration.ofHours(72);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    private final ManualFiscalDocumentRepository manualRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ManualInvoiceReconciliationService(
            ManualFiscalDocumentRepository manualRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.manualRepository = manualRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ManualFiscalDocumentResponseDto> reconcileManualBatch(UUID tenantId, ManualBatchRequestDto batchRequest) {
        if (batchRequest == null || batchRequest.documents() == null || batchRequest.documents().isEmpty()) {
            throw new BusinessException("EMPTY_MANUAL_BATCH", "Manual batch cannot be empty.", HttpStatus.BAD_REQUEST);
        }

        if (batchRequest.documents().size() > 200) {
            throw new BusinessException("BATCH_SIZE_EXCEEDED", "Manual batch cannot exceed 200 documents per submission.", HttpStatus.BAD_REQUEST);
        }

        List<ManualFiscalDocumentResponseDto> results = new ArrayList<>();
        Instant now = Instant.now();

        for (CreateManualFiscalDocumentDto dto : batchRequest.documents()) {
            // 1. Duplicate reference check
            if (manualRepository.existsByTenantIdAndManualBookAndManualDocumentNumber(
                    tenantId, dto.manualBook().trim(), dto.manualDocumentNumber().trim())) {
                throw new BusinessException("DUPLICATE_MANUAL_INVOICE",
                        "Manual invoice book=" + dto.manualBook() + " no=" + dto.manualDocumentNumber() +
                                " has already been imported or reconciled for this taxpayer.",
                        HttpStatus.CONFLICT);
            }

            // 2. Timing validation
            if (dto.originalIssueTime().isAfter(now.plusSeconds(300))) {
                throw new BusinessException("INVALID_ISSUE_TIME",
                        "Manual invoice original issue time cannot be in the future.",
                        HttpStatus.BAD_REQUEST);
            }

            // 3. Totals and Tax consistency
            BigDecimal expectedTotal = dto.subtotal().add(dto.taxAmount());
            if (dto.totalAmount().subtract(expectedTotal).abs().compareTo(TOLERANCE) > 0) {
                throw new BusinessException("TOTAL_MISMATCH",
                        "Manual invoice total amount (" + dto.totalAmount() + ") does not equal subtotal (" +
                                dto.subtotal() + ") + tax (" + dto.taxAmount() + ")",
                        HttpStatus.BAD_REQUEST);
            }

            if (dto.items() == null || dto.items().isEmpty()) {
                throw new BusinessException("EMPTY_LINE_ITEMS",
                        "Manual invoice must contain at least one line item.",
                        HttpStatus.BAD_REQUEST);
            }

            String itemsJson;
            try {
                itemsJson = objectMapper.writeValueAsString(dto.items());
            } catch (JsonProcessingException e) {
                throw new BusinessException("JSON_SERIALIZATION_ERROR", "Failed to serialize manual items JSON", HttpStatus.BAD_REQUEST);
            }

            Instant deadline = now.plus(STATUTORY_RECONCILIATION_WINDOW);

            ManualFiscalDocument doc = new ManualFiscalDocument(
                    UUID.randomUUID(),
                    tenantId,
                    dto.branchId(),
                    dto.manualDocumentNumber().trim(),
                    dto.manualBook().trim(),
                    dto.originalIssueTime(),
                    now,
                    dto.customerTin(),
                    dto.customerName(),
                    dto.totalAmount(),
                    dto.subtotal(),
                    dto.taxAmount(),
                    itemsJson,
                    dto.operatorId(),
                    dto.outageReference(),
                    deadline
            );

            ManualFiscalDocument saved = manualRepository.save(doc);

            auditService.recordEvent(
                    tenantId,
                    dto.operatorId(),
                    "MANUAL_INVOICE_IMPORTED",
                    "MANUAL_INVOICE",
                    saved.getId().toString(),
                    "BOOK=" + dto.manualBook() + ",NO=" + dto.manualDocumentNumber() + ",TOTAL=" + dto.totalAmount()
            );

            log.info("Imported manual invoice book={} no={} for tenant {} [OutageRef: {}]",
                    dto.manualBook(), dto.manualDocumentNumber(), tenantId, dto.outageReference());

            results.add(ManualFiscalDocumentResponseDto.fromEntity(saved));
        }

        return results;
    }

    @Transactional
    public ManualReprintResponseDto reprintManualInvoice(UUID tenantId, UUID documentId) {
        ManualFiscalDocument doc = manualRepository.findById(documentId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("MANUAL_INVOICE_NOT_FOUND",
                        "Manual fiscal document not found or belongs to another tenant.", HttpStatus.NOT_FOUND));

        // Increment reprint counter without modifying any original fiscal values
        doc.incrementReprintCount();
        ManualFiscalDocument saved = manualRepository.save(doc);

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "MANUAL_INVOICE_REPRINTED",
                "MANUAL_INVOICE",
                saved.getId().toString(),
                "COUNT=" + saved.getDuplicateReprintCount() + ",TAG=DUPLICATE"
        );

        log.warn("Reprint issued for manual invoice book={} no={} for tenant {} [ReprintCount: {}]",
                saved.getManualBook(), saved.getManualDocumentNumber(), tenantId, saved.getDuplicateReprintCount());

        return ManualReprintResponseDto.fromEntity(saved);
    }

    @Transactional
    public void recordGovernmentRegistration(UUID tenantId, UUID documentId, String authoritativeIrn) {
        ManualFiscalDocument doc = manualRepository.findById(documentId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("MANUAL_INVOICE_NOT_FOUND",
                        "Manual fiscal document not found.", HttpStatus.NOT_FOUND));

        doc.setEirsRegistrationState(ManualFiscalState.REGISTERED);
        doc.setIrn(authoritativeIrn);
        manualRepository.save(doc);

        auditService.recordEvent(
                tenantId,
                "SYSTEM",
                "MANUAL_INVOICE_EIRS_REGISTERED",
                "MANUAL_INVOICE",
                doc.getId().toString(),
                "IRN=" + authoritativeIrn
        );
    }
}
