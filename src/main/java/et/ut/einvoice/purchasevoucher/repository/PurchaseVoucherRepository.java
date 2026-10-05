package et.ut.einvoice.purchasevoucher.repository;

import et.ut.einvoice.purchasevoucher.domain.PurchaseVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseVoucherRepository extends JpaRepository<PurchaseVoucher, UUID> {
    Optional<PurchaseVoucher> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<PurchaseVoucher> findByVoucherNumberAndTenantId(String voucherNumber, UUID tenantId);
    Optional<PurchaseVoucher> findByRrnAndTenantId(String rrn, UUID tenantId);
    List<PurchaseVoucher> findByTenantIdOrderByTransactionDateDesc(UUID tenantId);
    List<PurchaseVoucher> findByTenantIdAndSupplierTinOrderByTransactionDateDesc(UUID tenantId, String supplierTin);
}
