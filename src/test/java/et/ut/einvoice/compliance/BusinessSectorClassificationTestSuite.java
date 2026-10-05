package et.ut.einvoice.compliance;

import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxpayer.domain.BusinessSector;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.BusinessSectorRepository;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.taxpayer.service.BusinessSectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BusinessSectorClassificationTestSuite {

    @Autowired
    private BusinessSectorService sectorService;

    @Autowired
    private BusinessSectorRepository sectorRepository;

    @Autowired
    private TaxpayerProfileRepository profileRepository;

    private UUID tenantGrocery;
    private UUID tenantTech;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        tenantGrocery = UUID.randomUUID();
        tenantTech = UUID.randomUUID();

        // Seed Annex 2 and general sectors if repository is empty in test profile
        if (sectorRepository.count() == 0) {
            sectorRepository.save(new BusinessSector("SEC-01", "Retail sale of food", "የምግብ ችርቻሮ ንግድ", true));
            sectorRepository.save(new BusinessSector("SEC-04", "Retail sale of automotive fuel", "የነዳጅና ነዳጅ ውጤቶች ችርቻሮ ንግድ", true));
            sectorRepository.save(new BusinessSector("SEC-08", "Retail sale of pharmaceutical and medical goods", "መድሃኒቶች ችርቻሮ ንግድ", true));
            sectorRepository.save(new BusinessSector("SEC-GEN-IT-SERVICES", "Information Technology & Consulting Services", "የኢንፎርሜሽን ቴክኖሎጂ", false));
        }

        TaxpayerProfile grocery = new TaxpayerProfile(
                tenantGrocery, "0011223344", "VAT-11223", "Addis Fresh Grocery PLC", "Addis Fresh",
                "Addis Ababa", "Bole", "0911002233", "grocery@ut.et", "SYS-GROCERY", "POS"
        );
        profileRepository.save(grocery);

        TaxpayerProfile tech = new TaxpayerProfile(
                tenantTech, "0099887766", "VAT-99887", "Horn Cloud Systems PLC", "Horn Cloud",
                "Addis Ababa", "Kirkos", "0922003344", "tech@ut.et", "SYS-TECH", "POS"
        );
        profileRepository.save(tech);
    }

    private void setTenantContext(UUID tenantId, String username) {
        TenantContextHolder.setContext(TenantContext.create(tenantId, username, Set.of("ROLE_TENANT_ADMIN")));
        var auth = new UsernamePasswordAuthenticationToken(
                username, "password", List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Stage 4: Annex 2 Sector Automatically Activates Mandatory Offline Continuity")
    void test_Annex2Sector_ActivatesMandatoryOfflineContinuity() {
        setTenantContext(tenantGrocery, "GROCERY_ADMIN");

        // Assign SEC-01 (Food retail - Annex 2)
        TaxpayerProfile updated = sectorService.assignSectorToTaxpayer(tenantGrocery, "SEC-01");
        assertNotNull(updated);
        assertEquals("SEC-01", updated.getSectorCode());
        assertTrue(updated.isMandatoryOfflineContinuity(), "Annex 2 food retail sector must activate mandatory offline continuity");

        assertTrue(sectorService.isOfflineContinuityMandatory(tenantGrocery));
    }

    @Test
    @DisplayName("Stage 4: General Non-Annex 2 Sector Does Not Force Offline Continuity")
    void test_GeneralSector_DoesNotForceOfflineContinuity() {
        setTenantContext(tenantTech, "TECH_ADMIN");

        // Assign IT Services (General sector)
        TaxpayerProfile updated = sectorService.assignSectorToTaxpayer(tenantTech, "SEC-GEN-IT-SERVICES");
        assertNotNull(updated);
        assertEquals("SEC-GEN-IT-SERVICES", updated.getSectorCode());
        assertFalse(updated.isMandatoryOfflineContinuity(), "General IT consulting is not an Annex 2 mandatory offline sector");

        assertFalse(sectorService.isOfflineContinuityMandatory(tenantTech));
    }

    @Test
    @DisplayName("Stage 4: Unknown Sector Code is Strictly Rejected")
    void test_UnknownSectorCode_Rejected() {
        setTenantContext(tenantGrocery, "GROCERY_ADMIN");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                sectorService.assignSectorToTaxpayer(tenantGrocery, "INVALID-SECTOR-CODE-999")
        );
        assertEquals("UNKNOWN_SECTOR", ex.getCode());
    }

    @Test
    @DisplayName("Stage 4: Querying Mandatory Offline Sectors Returns Statutory Annex 2 List")
    void test_QueryMandatoryOfflineSectors_ReturnsAnnex2() {
        List<BusinessSector> offlineSectors = sectorService.getMandatoryOfflineSectors();
        assertFalse(offlineSectors.isEmpty());
        assertTrue(offlineSectors.stream().allMatch(BusinessSector::isMandatoryOfflineContinuity));
        assertTrue(offlineSectors.stream().anyMatch(s -> "SEC-01".equals(s.getSectorCode())));
    }
}
