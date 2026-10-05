package et.ut.einvoice.taxpayer.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.BusinessSector;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.BusinessSectorRepository;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BusinessSectorService {

    private static final Logger log = LoggerFactory.getLogger(BusinessSectorService.class);

    private final BusinessSectorRepository sectorRepository;
    private final TaxpayerProfileRepository profileRepository;
    private final AuditService auditService;

    public BusinessSectorService(
            BusinessSectorRepository sectorRepository,
            TaxpayerProfileRepository profileRepository,
            AuditService auditService
    ) {
        this.sectorRepository = sectorRepository;
        this.profileRepository = profileRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BusinessSector> getAllActiveSectors() {
        return sectorRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<BusinessSector> getMandatoryOfflineSectors() {
        return sectorRepository.findByMandatoryOfflineContinuityTrue();
    }

    @Transactional(readOnly = true)
    public BusinessSector getSectorByCode(String sectorCode) {
        return sectorRepository.findById(sectorCode)
                .orElseThrow(() -> new BusinessException("UNKNOWN_SECTOR",
                        "Statutory business sector '" + sectorCode + "' is not recognized under Directive No. 1142/2026"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_TENANT_ADMIN') or hasAuthority('ROLE_PLATFORM_ADMIN')")
    public TaxpayerProfile assignSectorToTaxpayer(UUID tenantId, String sectorCode) {
        final UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getRequiredContext().tenantId();

        BusinessSector sector = getSectorByCode(sectorCode);
        TaxpayerProfile profile = profileRepository.findByTenantId(effectiveTenantId)
                .orElseThrow(() -> new BusinessException("TAXPAYER_PROFILE_NOT_FOUND",
                        "Taxpayer profile not found for tenant " + effectiveTenantId));

        profile.assignSector(sector.getSectorCode(), sector.isMandatoryOfflineContinuity());
        TaxpayerProfile saved = profileRepository.save(profile);

        auditService.recordEvent(
                effectiveTenantId,
                "SYSTEM",
                "ADMIN",
                "ASSIGN_SECTOR",
                "TAXPAYER_PROFILE",
                effectiveTenantId.toString(),
                "SECTOR=" + sector.getSectorCode() + ", MANDATORY_OFFLINE=" + sector.isMandatoryOfflineContinuity(),
                "127.0.0.1"
        );

        log.info("Assigned sector {} to tenant {} (Mandatory Offline Continuity: {})",
                sector.getSectorCode(), effectiveTenantId, sector.isMandatoryOfflineContinuity());

        return saved;
    }

    @Transactional(readOnly = true)
    public boolean isOfflineContinuityMandatory(UUID tenantId) {
        return profileRepository.findByTenantId(tenantId)
                .map(TaxpayerProfile::isMandatoryOfflineContinuity)
                .orElse(false);
    }
}
