package et.ut.einvoice.documents.repository;

import et.ut.einvoice.documents.domain.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {
    List<StoredDocument> findByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);
    Optional<StoredDocument> findByTenantIdAndInvoiceIdAndDocumentType(UUID tenantId, UUID invoiceId, String documentType);
}
