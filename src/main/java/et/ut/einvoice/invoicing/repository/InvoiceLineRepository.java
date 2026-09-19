package et.ut.einvoice.invoicing.repository;

import et.ut.einvoice.invoicing.domain.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    List<InvoiceLine> findAllByInvoiceId(UUID invoiceId);
}
