package et.ut.einvoice.compliance;

import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxation.domain.TaxCode;
import et.ut.einvoice.taxation.domain.TaxRule;
import et.ut.einvoice.taxation.repository.TaxRuleRepository;
import et.ut.einvoice.taxation.service.TaxEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TaxEngineBoundaryTestSuite {

    @Autowired
    private TaxEngine taxEngine;

    @Autowired
    private TaxRuleRepository taxRuleRepository;

    private final Instant H1_START = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant H1_END   = Instant.parse("2026-06-30T23:59:59Z");
    private final Instant H2_START = Instant.parse("2026-07-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        taxRuleRepository.deleteAll();

        // Rule V1: 15% VAT active from Jan 1 to June 30
        taxRuleRepository.save(new TaxRule(
                UUID.randomUUID(), "VAT15", "VAT", new BigDecimal("0.1500"),
                H1_START, H1_END, 1
        ));

        // Rule V2: 18% VAT active from July 1 onwards
        taxRuleRepository.save(new TaxRule(
                UUID.randomUUID(), "VAT15", "VAT", new BigDecimal("0.1800"),
                H2_START, null, 2
        ));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        taxRuleRepository.deleteAll();
    }

    @Test
    @DisplayName("Tax 1: Invoicing immediately before boundary uses Rule V1 (15%)")
    void test_ImmediatelyBeforeBoundary_UsesV1() {
        Instant beforeBoundary = Instant.parse("2026-06-30T23:59:58Z");
        var calc = taxEngine.calculateLineTax(
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, beforeBoundary
        );

        assertEquals(new BigDecimal("15.00"), calc.taxAmount());
        assertEquals(new BigDecimal("115.00"), calc.totalLineAmount());
        assertEquals(1, calc.ruleVersion());
    }

    @Test
    @DisplayName("Tax 2: Invoicing at last second of Rule V1 window uses Rule V1 (15%)")
    void test_AtLastSecondOfV1_UsesV1() {
        var calc = taxEngine.calculateLineTax(
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, H1_END
        );

        assertEquals(new BigDecimal("15.00"), calc.taxAmount());
        assertEquals(new BigDecimal("115.00"), calc.totalLineAmount());
        assertEquals(1, calc.ruleVersion());
    }

    @Test
    @DisplayName("Tax 3: Invoicing exactly on boundary (first second of July 1) uses Rule V2 (18%)")
    void test_ExactlyOnBoundary_UsesV2() {
        var calc = taxEngine.calculateLineTax(
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, H2_START
        );

        assertEquals(new BigDecimal("18.00"), calc.taxAmount());
        assertEquals(new BigDecimal("118.00"), calc.totalLineAmount());
        assertEquals(2, calc.ruleVersion());
    }

    @Test
    @DisplayName("Tax 4: Invoicing after boundary uses Rule V2 (18%)")
    void test_AfterBoundary_UsesV2() {
        Instant afterBoundary = Instant.parse("2026-08-15T10:30:00Z");
        var calc = taxEngine.calculateLineTax(
                BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, afterBoundary
        );

        assertEquals(new BigDecimal("18.00"), calc.taxAmount());
        assertEquals(new BigDecimal("118.00"), calc.totalLineAmount());
        assertEquals(2, calc.ruleVersion());
    }

    @Test
    @DisplayName("Tax 5: Overlapping active rules for the same tax code is rejected with TAX_RULE_OVERLAP_AMBIGUITY")
    void test_OverlappingActiveRules_RejectedWithAmbiguityError() {
        // Insert conflicting overlapping rule also active on July 15
        taxRuleRepository.save(new TaxRule(
                UUID.randomUUID(), "VAT15", "VAT", new BigDecimal("0.2000"),
                Instant.parse("2026-07-10T00:00:00Z"), Instant.parse("2026-07-20T00:00:00Z"), 3
        ));

        Instant conflictDate = Instant.parse("2026-07-15T12:00:00Z");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(
                        BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO,
                        TaxCode.VAT15, BigDecimal.ZERO, conflictDate
                )
        );

        assertEquals("TAX_RULE_OVERLAP_AMBIGUITY", ex.getCode());
    }
}
