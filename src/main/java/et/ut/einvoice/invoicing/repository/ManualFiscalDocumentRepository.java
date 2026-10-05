package et.ut.einvoice.invoicing.repository;

import et.ut.einvoice.invoicing.domain.ManualFiscalDocument;
import et.ut.einvoice.invoicing.domain.ManualFiscalState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManualFiscalDocumentRepository extends JpaRepository<ManualFiscalDocument, UUID> {

    Optional<ManualFiscalDocument> findByTenantIdAndManualBookAndManualDocumentNumber(
            UUID tenantId, String manualBook, String manualDocumentNumber);

    boolean existsByTenantIdAndManualBookAndManualDocumentNumber(
            UUID tenantId, String manualBook, String manualDocumentNumber);

    Page<ManualFiscalDocument> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<ManualFiscalDocument> findAllByTenantIdAndEirsRegistrationState(
            UUID tenantId, ManualFiscalState state);
}
