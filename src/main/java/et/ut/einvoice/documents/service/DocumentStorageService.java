package et.ut.einvoice.documents.service;

import et.ut.einvoice.compliance.service.InsaDigitalSignatureService;
import et.ut.einvoice.documents.domain.StoredDocument;
import et.ut.einvoice.documents.repository.StoredDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final StoredDocumentRepository repository;
    private final InsaDigitalSignatureService signatureService;

    public DocumentStorageService(StoredDocumentRepository repository, InsaDigitalSignatureService signatureService) {
        this.repository = repository;
        this.signatureService = signatureService;
    }

    @Transactional
    public StoredDocument storeDocument(UUID tenantId, UUID invoiceId, String documentType, String content, String contentType) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = signatureService.computeSha256Hash(content);
        String storagePath = "/documents/" + tenantId + "/" + (invoiceId != null ? invoiceId.toString() : "common") + "/" + documentType.toLowerCase() + ".html";

        StoredDocument doc = new StoredDocument(
                UUID.randomUUID(),
                tenantId,
                invoiceId,
                documentType,
                storagePath,
                hash,
                contentType,
                bytes.length
        );

        StoredDocument saved = repository.save(doc);
        log.info("Stored document metadata {} for invoice {}: path={}, size={} bytes, hash={}",
                saved.getId(), invoiceId, storagePath, bytes.length, hash);
        return saved;
    }
}
