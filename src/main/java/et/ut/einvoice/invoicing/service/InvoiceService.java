package et.ut.einvoice.invoicing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.documents.service.QrCodeService;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.domain.GovernmentSubmission;
import et.ut.einvoice.government.domain.GovernmentSubmissionStatus;
import et.ut.einvoice.government.repository.GovernmentSubmissionRepository;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.invoicing.domain.InvoiceLine;
import et.ut.einvoice.invoicing.domain.InvoiceStatus;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.invoicing.dto.InvoiceResponseDto;
import et.ut.einvoice.invoicing.repository.InvoiceRepository;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.events.DomainEventPublisher;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.platform.idempotency.domain.IdempotencyRecord;
import et.ut.einvoice.platform.idempotency.service.IdempotencyService;
import et.ut.einvoice.platform.outbox.domain.OutboxEvent;
import et.ut.einvoice.platform.outbox.service.OutboxService;
import et.ut.einvoice.platform.outbox.worker.OutboxRelayWorker;
import et.ut.einvoice.taxation.domain.TaxCode;
import et.ut.einvoice.taxation.service.TaxEngine;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.GovernmentStatus;
import et.ut.einvoice.tenancy.domain.SubscriptionStatus;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final GovernmentSubmissionRepository submissionRepository;
    private final TaxEngine taxEngine;
    private final GovernmentRegistrationProvider governmentRegistrationProvider;
    private final QrCodeService qrCodeService;
    private final InsaDigitalSignatureService signatureService;
    private final DomainEventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final TenantSequenceService sequenceService;
    private final ObjectMapper objectMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final TenantRepository tenantRepository;
    private final et.ut.einvoice.government.service.MorInvoiceCanonicalizationService canonicalizationService;
    private final et.ut.einvoice.customer.service.CustomerService customerService;
    private final et.ut.einvoice.notifications.service.InvoiceNotificationPolicyService notificationPolicyService;
    private final et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository notificationOutboxRepository;
    private final et.ut.einvoice.notifications.service.InvoiceNotificationTemplateService notificationTemplateService;
    private final et.ut.einvoice.notifications.metrics.SmsMetrics smsMetrics;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            GovernmentSubmissionRepository submissionRepository,
            TaxEngine taxEngine,
            GovernmentRegistrationProvider governmentRegistrationProvider,
            QrCodeService qrCodeService,
            InsaDigitalSignatureService signatureService,
            DomainEventPublisher eventPublisher,
            IdempotencyService idempotencyService,
            OutboxService outboxService,
            AuditService auditService,
            TenantSequenceService sequenceService,
            ObjectMapper objectMapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TenantRepository tenantRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.government.service.MorInvoiceCanonicalizationService canonicalizationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.customer.service.CustomerService customerService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.service.InvoiceNotificationPolicyService notificationPolicyService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.repository.InvoiceNotificationOutboxRepository notificationOutboxRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.service.InvoiceNotificationTemplateService notificationTemplateService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            et.ut.einvoice.notifications.metrics.SmsMetrics smsMetrics
    ) {
        this.invoiceRepository = invoiceRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.submissionRepository = submissionRepository;
        this.taxEngine = taxEngine;
        this.governmentRegistrationProvider = governmentRegistrationProvider;
        this.qrCodeService = qrCodeService;
        this.signatureService = signatureService;
        this.eventPublisher = eventPublisher;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.sequenceService = sequenceService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.tenantRepository = tenantRepository;
        this.canonicalizationService = canonicalizationService != null ? canonicalizationService : new et.ut.einvoice.government.service.MorInvoiceCanonicalizationService(objectMapper, qrCodeService);
        this.customerService = customerService;
        this.notificationPolicyService = notificationPolicyService;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.notificationTemplateService = notificationTemplateService;
        this.smsMetrics = smsMetrics;
    }

    /**
     * Synchronous Invoice Creation & Registration.
     * Architectural guarantee: Database transaction COMMITS before any external EIRS HTTP call.
     */
    public InvoiceResponseDto createAndRegisterInvoice(CreateInvoiceRequest request, String idempotencyKey) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        UUID tenantId = ctx.tenantId();
        String clientId = ctx.clientId() != null ? ctx.clientId() : "DEFAULT_CLIENT";

        // 0. Boundary & Authorization Enforcement: Tenant Active, Commercial Subscription, Government Authorization
        if (tenantRepository != null) {
            tenantRepository.findById(tenantId).ifPresent(tenant -> {
                if (tenant.getStatus() != TenantStatus.ACTIVE) {
                    throw new BusinessException(
                            "TENANT_SUSPENDED",
                            "Tenant organization is suspended or deactivated.",
                            "የተጠቃሚ ድርጅት ታግዷል ወይም ተዘግቷል።",
                            HttpStatus.FORBIDDEN
                    );
                }
                if (tenant.getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_SUSPENDED ||
                    tenant.getSubscriptionStatus() == SubscriptionStatus.SUBSCRIPTION_EXPIRED) {
                    throw new BusinessException(
                            "SUBSCRIPTION_SUSPENDED",
                            "Commercial SaaS subscription is suspended or expired. Payment required to issue invoices.",
                            "የደንበኝነት ምዝገባዎ አብቅቷል ወይም ታግዷል። እባክዎ ክፍያ ይፈጽሙ።",
                            HttpStatus.PAYMENT_REQUIRED
                    );
                }
                if (tenant.getGovernmentStatus() != GovernmentStatus.GOVERNMENT_ACTIVE) {
                    throw new BusinessException(
                            "GOVERNMENT_AUTHORIZATION_REQUIRED",
                            "Tenant is not authorized by Ministry of Revenues / EIRS for electronic invoice issuance.",
                            "ድርጅቱ ከገቢዎች ሚኒስቴር የኤሌክትሮኒክ ደረሰኝ ማውጣት ፈቃድ የለውም ወይም ታግዷል።",
                            HttpStatus.FORBIDDEN
                    );
                }
            });
        }

        // 1. Idempotency Check with Request Hash Verification
        String payloadJson = serializePayload(request);
        Optional<IdempotencyRecord> idempotentMatch = idempotencyService.checkAndLock(tenantId, clientId, idempotencyKey, payloadJson);
        if (idempotentMatch.isPresent()) {
            IdempotencyRecord record = idempotentMatch.get();
            if (record.getResponsePayload() != null) {
                log.info("Returning cached idempotent response for tenant {} client {} key {}", tenantId, clientId, idempotencyKey);
                return deserializeResponse(record.getResponsePayload());
            }
            // Crash recovery: check if invoice was already committed with this idempotency key
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<Invoice> existingInvoice = invoiceRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
                if (existingInvoice.isPresent()) {
                    Invoice inv = existingInvoice.get();
                    log.info("Recovered committed invoice for idempotency key {}: {}", idempotencyKey, inv.getId());
                    InvoiceResponseDto recovered = InvoiceResponseDto.fromEntity(inv);
                    idempotencyService.markCompleted(tenantId, clientId, idempotencyKey, inv.getId().toString(), serializeResponse(recovered));
                    return recovered;
                }
            }
        }

        // 1b. Document number reconciliation (prevents duplicate invoice constraint violation on re-submission)
        if (request.customDocumentNumber() != null && !request.customDocumentNumber().isBlank()) {
            Optional<Invoice> existingByDoc = invoiceRepository.findByTenantIdAndDocumentNumber(tenantId, request.customDocumentNumber());
            if (existingByDoc.isPresent()) {
                Invoice inv = existingByDoc.get();
                log.info("Recovered committed invoice for custom document number {}: {}", request.customDocumentNumber(), inv.getId());
                return InvoiceResponseDto.fromEntity(inv);
            }
        }

        // 2. Validate line items and B2B / B2C business requirements
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(
                    "EMPTY_LINE_ITEMS",
                    "An invoice must contain at least one line item pursuant to Directive No. 1142/2018 Art. 4(1)(a).",
                    "ደረሰኝ ቢያንስ አንድ የዕቃ ወይም አገልግሎት ዝርዝር መያዝ አለበት።",
                    HttpStatus.BAD_REQUEST
            );
        }
        validateBuyerDetails(request);

        TaxpayerProfile seller = taxpayerProfileRepository.findById(tenantId)
                .orElseGet(() -> createDefaultTaxpayerProfile(tenantId));

        // 3. Atomically persist invoice & outbox event within a clean DB transaction
        PersistedInvoiceBundle bundle = transactionTemplate.execute(status ->
                persistInvoiceAndOutbox(request, idempotencyKey, tenantId, clientId, seller, payloadJson)
        );

        // 4. Authoritative EIRS Submission via Single Outbox Dispatcher (Executed outside database transaction)
        Invoice invoice = bundle.invoice();
        GovernmentSubmission submission = bundle.submission();

        try {
            var regResult = governmentRegistrationProvider.registerInvoice(invoice, seller, "AUTO_AUTH");

            if (regResult.success()) {
                invoice.markRegistered(
                        regResult.irn(),
                        regResult.rrn(),
                        regResult.ackDate(),
                        regResult.signedQr(),
                        regResult.signedInvoice()
                );
                submission.markAccepted(regResult.irn());
                saveInvoiceAndSubmission(invoice, submission, seller.getTradeName() != null ? seller.getTradeName() : seller.getLegalName(), bundle.outboxEvent().getId().toString());
                outboxService.markPublished(bundle.outboxEvent().getId());

                eventPublisher.publish(new InvoiceRegisteredEvent(
                        invoice.getId(),
                        tenantId,
                        invoice.getIrn(),
                        invoice.getBuyerEmail(),
                        invoice.getBuyerPhone()
                ));
            } else if ("SEQUENCE_MISMATCH".equals(regResult.errorCode()) && regResult.expectedNextDoc() != null) {
                // Hardened sequence recovery under tenant sequence lock
                handleSequenceMismatchAndRetry(invoice, submission, bundle.outboxEvent(), seller, regResult);
            } else {
                submission.markRejected(regResult.errorCode(), regResult.errorMessage());
                submissionRepository.save(submission);
                fallbackToOfflineBuffer(invoice, regResult.errorMessage());
            }
        } catch (Exception ex) {
            log.error("Network or Gateway communication failure connecting to MoR: {}", ex.getMessage());
            submission.markUnknown("GATEWAY_TIMEOUT", ex.getMessage());
            submissionRepository.save(submission);
            fallbackToOfflineBuffer(invoice, ex.getMessage());
        }

        // 5. Ensure local QR code is generated if not provided by gateway
        ensureQrCode(invoice, seller);
        Invoice saved = invoiceRepository.save(invoice);

        InvoiceResponseDto response = InvoiceResponseDto.fromEntity(saved);
        idempotencyService.markCompleted(tenantId, clientId, idempotencyKey, saved.getId().toString(), serializeResponse(response));
        return response;
    }

    /**
     * Atomic database commit for invoice creation and outbox event enqueueing.
     */
    @Transactional
    public PersistedInvoiceBundle persistInvoiceAndOutbox(
            CreateInvoiceRequest request,
            String idempotencyKey,
            UUID tenantId,
            String clientId,
            TaxpayerProfile seller,
            String payloadJson
    ) {
        long nextCounter = sequenceService.allocateNextCounter(tenantId);
        String docNumber = request.customDocumentNumber() != null ? request.customDocumentNumber() : String.valueOf(nextCounter);

        Optional<Invoice> latestInvoice = invoiceRepository.findLatestInvoice(tenantId);
        String previousIrn = latestInvoice.map(Invoice::getIrn).orElse("");

            Invoice invoice = new Invoice(
                    UUID.randomUUID(),
                    tenantId,
                    docNumber,
                    nextCounter,
                    Instant.now(),
                    request.transactionType(),
                    request.paymentMode(),
                    request.paymentTerm()
            );
            invoice.setPreviousIrn(previousIrn);
            invoice.setIdempotencyKey(idempotencyKey);
            if (notificationTemplateService != null) {
                invoice.setPublicVerificationToken(notificationTemplateService.generateVerificationToken());
            }

            if (request.buyer() != null) {
                invoice.setBuyerLegalName(request.buyer().legalName());
                invoice.setBuyerTin(request.buyer().normalizedTin());
                invoice.setBuyerVatNumber(request.buyer().vatNumber());
                invoice.setBuyerIdNumber(request.buyer().idNumber());
                invoice.setBuyerIdType(request.buyer().idType() != null ? request.buyer().idType() : "TIN");
                invoice.setBuyerPhone(request.buyer().phone());
                invoice.setBuyerEmail(request.buyer().email());
                invoice.setBuyerCountry(request.buyer().country() != null ? request.buyer().country() : "ET");
                invoice.setBuyerRegion(request.buyer().region());
                invoice.setBuyerCity(request.buyer().city());
                invoice.setBuyerZone(request.buyer().zone());
                invoice.setBuyerWoreda(request.buyer().woreda());
                invoice.setBuyerKebele(request.buyer().kebele());
                invoice.setBuyerHouseNo(request.buyer().houseNo());

                if (Boolean.TRUE.equals(request.saveCustomerToMaster()) && request.buyer().legalName() != null && !request.buyer().legalName().isBlank() && customerService != null) {
                    try {
                        customerService.upsertFromInvoiceBuyer(
                                tenantId,
                                invoice.getBranchId(),
                                request.buyer().legalName(),
                                request.buyer().normalizedTin(),
                                request.buyer().vatNumber(),
                                request.buyer().phone(),
                                request.buyer().email(),
                                request.buyer().country(),
                                request.buyer().region(),
                                request.buyer().city(),
                                request.buyer().zone(),
                                request.buyer().woreda(),
                                request.buyer().kebele(),
                                request.buyer().houseNo(),
                                request.buyer().idType(),
                                request.buyer().idNumber()
                        );
                    } catch (Exception e) {
                        log.warn("Customer master auto-save from invoice failed: {}", e.getMessage());
                    }
                }
            }

            int lineNum = 1;
            for (var itemReq : request.items()) {
                TaxCode taxCode = TaxCode.fromCode(itemReq.taxCode());
                var calc = taxEngine.calculateLineTax(
                        itemReq.quantity(),
                        itemReq.unitPrice(),
                        itemReq.discount(),
                        taxCode,
                        itemReq.exciseRate(),
                        Instant.now()
                );

                InvoiceLine line = new InvoiceLine(
                        UUID.randomUUID(),
                        tenantId,
                        lineNum++,
                        itemReq.itemCode(),
                        itemReq.productDescription(),
                        itemReq.natureOfSupplies(),
                        itemReq.unit(),
                        itemReq.quantity(),
                        itemReq.unitPrice(),
                        itemReq.discount(),
                        calc.preTaxValue(),
                        taxCode.name(),
                        taxCode.getRate(),
                        calc.taxAmount(),
                        calc.exciseAmount(),
                        calc.totalLineAmount()
                );
                invoice.addLine(line);
            }
            invoice.recalculateTotals();
            Invoice savedInvoice = invoiceRepository.save(invoice);

            // Create first-class GovernmentSubmission record
            String submissionId = "SUB-" + savedInvoice.getId();
            String requestHash = signatureService.computeSha256Hash(payloadJson);
            GovernmentSubmission submission = new GovernmentSubmission(
                    UUID.randomUUID(),
                    tenantId,
                    savedInvoice.getId(),
                    "MoR-EIRS",
                    "v1.0",
                    submissionId,
                    requestHash
            );
            GovernmentSubmission savedSubmission = submissionRepository.save(submission);

            // Enqueue transactional outbox event
            OutboxEvent outboxEvent = outboxService.enqueueEvent(
                    tenantId,
                    "INVOICE",
                    savedInvoice.getId().toString(),
                    "INVOICE_REGISTRATION",
                    payloadJson
            );

            auditService.recordEvent(
                    tenantId,
                    "INVOICE",
                    clientId,
                    "CREATE_COMMITTED",
                    "INVOICE",
                    savedInvoice.getId().toString(),
                    payloadJson,
                    "127.0.0.1"
            );

            return new PersistedInvoiceBundle(savedInvoice, savedSubmission, outboxEvent);
    }

    /**
     * Hardened sequence recovery: acquires tenant database sequence lock, verifies state, logs audit, and retries.
     */
    private void handleSequenceMismatchAndRetry(
            Invoice invoice,
            GovernmentSubmission submission,
            OutboxEvent outboxEvent,
            TaxpayerProfile seller,
            GovernmentRegistrationProvider.GovernmentRegistrationResult regResult
    ) {
        UUID tenantId = invoice.getTenantId();
        sequenceService.adjustCounterIfHigher(tenantId, regResult.expectedNextCounter());

        log.warn("Acquired sequence lock for tenant {}: adjusting docNumber from {} to {}, counter from {} to {}",
                tenantId, invoice.getDocumentNumber(), regResult.expectedNextDoc(), invoice.getInvoiceCounter(), regResult.expectedNextCounter());

        auditService.recordEvent(
                tenantId,
                "SEQUENCE",
                "SYSTEM",
                "SEQUENCE_RECOVERY_ADJUST",
                "INVOICE",
                invoice.getId().toString(),
                String.format("oldDoc=%s, newDoc=%s, oldCounter=%d, newCounter=%d",
                        invoice.getDocumentNumber(), regResult.expectedNextDoc(), invoice.getInvoiceCounter(), regResult.expectedNextCounter()),
                "127.0.0.1"
        );

        invoice.setDocumentNumber(String.valueOf(regResult.expectedNextDoc()));
        invoice.setInvoiceCounter(regResult.expectedNextCounter());

        var retryResult = governmentRegistrationProvider.registerInvoice(invoice, seller, "AUTO_AUTH");
        if (retryResult.success()) {
            invoice.markRegistered(
                    retryResult.irn(),
                    retryResult.rrn(),
                    retryResult.ackDate(),
                    retryResult.signedQr(),
                    retryResult.signedInvoice()
            );
            submission.markAccepted(retryResult.irn());
            saveInvoiceAndSubmission(invoice, submission);
            outboxService.markPublished(outboxEvent.getId());
        } else {
            submission.markRejected(retryResult.errorCode(), retryResult.errorMessage());
            submissionRepository.save(submission);
            fallbackToOfflineBuffer(invoice, retryResult.errorMessage());
        }
    }

    private void ensureQrCode(Invoice invoice, TaxpayerProfile seller) {
        if (invoice.getSignedQr() == null || invoice.getSignedQr().isBlank()) {
            String qrPayload;
            if (canonicalizationService != null) {
                qrPayload = canonicalizationService.buildCanonicalQrData(
                        invoice, seller, invoice.getSignedInvoice(), invoice.getAckDate()
                );
            } else {
                qrPayload = String.format("SELLER:%s|DOC:%s|TOTAL:%s|IRN:%s",
                        seller.getTin(), invoice.getDocumentNumber(), invoice.getGrandTotal(),
                        invoice.getIrn() != null ? invoice.getIrn() : "OFFLINE");
            }
            String qrBase64 = qrCodeService.generateQrCodeBase64(qrPayload, 600, 600);
            if (invoice.getStatus() == InvoiceStatus.OFFLINE_BUFFERED) {
                invoice.setOfflineQr(qrBase64);
            } else {
                invoice.markRegistered(
                        invoice.getIrn() != null ? invoice.getIrn() : "OFFLINE-" + UUID.randomUUID(),
                        "RRN-" + invoice.getDocumentNumber(),
                        invoice.getAckDate() != null ? invoice.getAckDate() : Instant.now().toString(),
                        qrBase64,
                        invoice.getSignedInvoice() != null ? invoice.getSignedInvoice() : ""
                );
            }
        }
    }

    @Transactional
    public void saveInvoiceAndSubmission(Invoice invoice, GovernmentSubmission submission) {
        saveInvoiceAndSubmission(invoice, submission, null, null);
    }

    @Transactional
    public void saveInvoiceAndSubmission(Invoice invoice, GovernmentSubmission submission, String sellerName, String correlationId) {
        if (invoice.getPublicVerificationToken() == null && notificationTemplateService != null) {
            invoice.setPublicVerificationToken(notificationTemplateService.generateVerificationToken());
        }
        invoiceRepository.save(invoice);
        if (submission != null) {
            submissionRepository.save(submission);
        }

        if (notificationPolicyService != null && notificationOutboxRepository != null && invoice.getStatus() == InvoiceStatus.REGISTERED) {
            var decision = notificationPolicyService.evaluateRegistrationNotification(
                    invoice,
                    sellerName != null ? sellerName : "Seller",
                    correlationId
            );
            if (decision.shouldNotify() && decision.outboxRecord() != null) {
                notificationOutboxRepository.save(decision.outboxRecord());
                if (smsMetrics != null) {
                    smsMetrics.recordCreated(decision.outboxRecord().getNotificationType());
                }
                if (auditService != null) {
                    auditService.recordEvent(
                            invoice.getTenantId(),
                            "SMS_NOTIFICATION",
                            "SYSTEM",
                            et.ut.einvoice.audit.domain.AuditAction.SMS_NOTIFICATION_CREATED.name(),
                            "INVOICE",
                            invoice.getId().toString(),
                            "outbox_id=" + decision.outboxRecord().getId() + ";party_id=" + decision.outboxRecord().getRecipientPartyId(),
                            "127.0.0.1"
                    );
                }
            }
        }
    }

    private void fallbackToOfflineBuffer(Invoice invoice, String errorMsg) {
        log.warn("Switching invoice {} to OFFLINE_BUFFERED mode due to EIRS condition: {}", invoice.getId(), errorMsg);
        invoice.markOfflineBuffered();
    }

    private void validateBuyerDetails(CreateInvoiceRequest request) {
        if (request.transactionType() == TransactionType.B2B) {
            if (request.buyer() == null || request.buyer().tin() == null || request.buyer().tin().isBlank()) {
                throw new BusinessException(
                        "INVALID_BUYER_TIN",
                        "Buyer TIN is mandatory for B2B transactions pursuant to Directive No. 1142/2018 EC (2026 GC) Art. 4(1)(b).",
                        "ለንግድ ለንግድ (B2B) ግብይቶች የገዢው የታክስ ከፋይ መለያ ቁጥር (TIN) መሞላት ግዴታ ነው።",
                        HttpStatus.BAD_REQUEST
                );
            }
            if (request.buyer().legalName() == null || request.buyer().legalName().isBlank()) {
                throw new BusinessException(
                        "INVALID_BUYER_NAME",
                        "Buyer legal name is mandatory for B2B transactions.",
                        "የገዢው ህጋዊ ስም መሞላት አለበት።",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        // Validate format of TIN whenever provided across all transaction types
        if (request.buyer() != null && request.buyer().tin() != null && !request.buyer().tin().isBlank()) {
            String cleanTin = request.buyer().tin().trim();
            if (!cleanTin.matches("^\\d{10}$") || cleanTin.equals("0000000000")) {
                throw new BusinessException(
                        "INVALID_BUYER_TIN",
                        "The provided Buyer TIN [" + cleanTin + "] is invalid. Ethiopian TIN must contain exactly 10 numeric digits.",
                        "የቀረበው የገዢው ታክስ ከፋይ መለያ ቁጥር (TIN) ትክክለኛ አይደለም፤ በትክክል 10 አሃዞች መሆን አለበት።",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        // Validate line items
        if (request.items() != null) {
            for (var item : request.items()) {
                if (item.quantity() == null || item.quantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(
                            "INVALID_ITEM_QUANTITY",
                            "Item quantity must be greater than zero for item " + item.itemCode(),
                            "የዕቃው መጠን ከዜሮ በላይ መሆን አለበት።",
                            HttpStatus.BAD_REQUEST
                    );
                }
                if (item.unitPrice() == null || item.unitPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new BusinessException(
                            "INVALID_ITEM_PRICE",
                            "Unit price cannot be negative for item " + item.itemCode(),
                            "የነጠላ ዋጋ ከዜሮ ማነስ የለበትም።",
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
        }
    }

    private TaxpayerProfile createDefaultTaxpayerProfile(UUID tenantId) {
        String cleanUuid = tenantId.toString().replace("-", "");
        String uniqueTin = "9" + cleanUuid.replaceAll("[^0-9]", "1").substring(0, 9);
        TaxpayerProfile p = new TaxpayerProfile(
                tenantId,
                uniqueTin,
                "VAT-" + cleanUuid.substring(0, 8),
                "UT Test Enterprise PLC",
                "UT Retail",
                "14",
                "03",
                "+251911000000",
                "tax@" + cleanUuid.substring(0, 6) + ".utsystems.et",
                "8EFBBDD7FF",
                "ERP"
        );
        return taxpayerProfileRepository.save(p);
    }

    private String serializePayload(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String serializeResponse(InvoiceResponseDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            return "{}";
        }
    }

    private InvoiceResponseDto deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, InvoiceResponseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cached invoice response", e);
        }
    }

    public InvoiceResponseDto getInvoiceById(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found with ID " + id, HttpStatus.NOT_FOUND));
        return InvoiceResponseDto.fromEntity(invoice);
    }

    public InvoiceResponseDto getInvoiceByIrn(String irn) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIrnAndTenantId(irn, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found with IRN " + irn, HttpStatus.NOT_FOUND));
        return InvoiceResponseDto.fromEntity(invoice);
    }

    public Page<InvoiceResponseDto> listInvoices(InvoiceStatus status, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        if (status != null) {
            return invoiceRepository.findAllByTenantIdAndStatus(tenantId, status, pageable).map(InvoiceResponseDto::fromEntity);
        }
        return invoiceRepository.findAllByTenantId(tenantId, pageable).map(InvoiceResponseDto::fromEntity);
    }

    @Transactional
    public Invoice recordReprint(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found with ID " + id, HttpStatus.NOT_FOUND));
        invoice.recordReprint();
        auditService.recordEvent(tenantId, "INVOICE", "USER", "REPRINT_INVOICE", "INVOICE", id.toString(), "COUNT=" + invoice.getReprintCount(), "127.0.0.1");
        return invoiceRepository.save(invoice);
    }

    public Page<InvoiceResponseDto> getInvoices(UUID tenantId, Pageable pageable) {
        return invoiceRepository.findAllByTenantId(tenantId, pageable).map(InvoiceResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public et.ut.einvoice.invoicing.dto.InvoiceSummaryDto getTenantInvoiceSummary(UUID tenantId) {
        java.time.Instant startOfToday = java.time.LocalDate.now(java.time.ZoneId.of("UTC")).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant();
        java.util.List<Invoice> allTenantInvoices = invoiceRepository.findAllByTenantId(tenantId, Pageable.unpaged()).getContent();

        long todayCount = 0;
        java.math.BigDecimal todayGross = java.math.BigDecimal.ZERO;
        java.math.BigDecimal todayVat = java.math.BigDecimal.ZERO;
        long registeredCount = 0;
        long pendingCount = 0;
        java.math.BigDecimal totalGross = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalVat = java.math.BigDecimal.ZERO;

        for (Invoice inv : allTenantInvoices) {
            java.math.BigDecimal invTotal = inv.getGrandTotal() != null ? inv.getGrandTotal() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal invVat = inv.getTaxTotal() != null ? inv.getTaxTotal() : java.math.BigDecimal.ZERO;

            totalGross = totalGross.add(invTotal);
            totalVat = totalVat.add(invVat);

            if (inv.getInvoiceDate() != null && inv.getInvoiceDate().isAfter(startOfToday)) {
                todayCount++;
                todayGross = todayGross.add(invTotal);
                todayVat = todayVat.add(invVat);
            }

            if (inv.getStatus() == InvoiceStatus.REGISTERED) {
                registeredCount++;
            } else if (inv.getStatus() == InvoiceStatus.PENDING_REGISTRATION) {
                pendingCount++;
            }
        }

        return new et.ut.einvoice.invoicing.dto.InvoiceSummaryDto(
                todayCount,
                todayGross,
                todayVat,
                allTenantInvoices.size(),
                totalGross,
                totalVat,
                registeredCount,
                pendingCount
        );
    }

    public record PersistedInvoiceBundle(
            Invoice invoice,
            GovernmentSubmission submission,
            OutboxEvent outboxEvent
    ) {}

    public record InvoiceRegisteredEvent(
            UUID invoiceId,
            UUID tenantId,
            String irn,
            String buyerEmail,
            String buyerPhone
    ) {}
}
