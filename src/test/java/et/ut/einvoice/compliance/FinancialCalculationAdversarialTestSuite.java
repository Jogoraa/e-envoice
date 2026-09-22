package et.ut.einvoice.compliance;

import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxation.domain.TaxCode;
import et.ut.einvoice.taxation.service.TaxEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FinancialCalculationAdversarialTestSuite {

    @Autowired
    private TaxEngine taxEngine;

    @Test
    @DisplayName("Financial 1: Negative quantity is strictly rejected with INVALID_QUANTITY")
    void test_NegativeQuantity_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(new BigDecimal("-1.0"), new BigDecimal("100.00"), BigDecimal.ZERO, TaxCode.VAT15, BigDecimal.ZERO, Instant.now())
        );
        assertEquals("INVALID_QUANTITY", ex.getCode());
    }

    @Test
    @DisplayName("Financial 2: Zero quantity is strictly rejected with INVALID_QUANTITY")
    void test_ZeroQuantity_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(BigDecimal.ZERO, new BigDecimal("100.00"), BigDecimal.ZERO, TaxCode.VAT15, BigDecimal.ZERO, Instant.now())
        );
        assertEquals("INVALID_QUANTITY", ex.getCode());
    }

    @Test
    @DisplayName("Financial 3: Negative unit price is strictly rejected with INVALID_UNIT_PRICE")
    void test_NegativeUnitPrice_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(BigDecimal.ONE, new BigDecimal("-50.00"), BigDecimal.ZERO, TaxCode.VAT15, BigDecimal.ZERO, Instant.now())
        );
        assertEquals("INVALID_UNIT_PRICE", ex.getCode());
    }

    @Test
    @DisplayName("Financial 4: Negative discount is strictly rejected with INVALID_DISCOUNT")
    void test_NegativeDiscount_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("-10.00"), TaxCode.VAT15, BigDecimal.ZERO, Instant.now())
        );
        assertEquals("INVALID_DISCOUNT", ex.getCode());
    }

    @Test
    @DisplayName("Financial 5: Discount exceeding gross line total is strictly rejected with DISCOUNT_EXCEEDS_SUBTOTAL")
    void test_DiscountExceedingSubtotal_Rejected() {
        // Gross total is 100.00, discount is 150.00
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("150.00"), TaxCode.VAT15, BigDecimal.ZERO, Instant.now())
        );
        assertEquals("DISCOUNT_EXCEEDS_SUBTOTAL", ex.getCode());
    }

    @Test
    @DisplayName("Financial 6: Negative excise tax rate is strictly rejected with INVALID_EXCISE_RATE")
    void test_NegativeExciseRate_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taxEngine.calculateLineTax(BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, TaxCode.VAT15, new BigDecimal("-0.10"), Instant.now())
        );
        assertEquals("INVALID_EXCISE_RATE", ex.getCode());
    }

    @Test
    @DisplayName("Financial 7: Rounding boundary verification at 0.005 uses HALF_UP")
    void test_RoundingBoundary_HalfUp() {
        // Price = 0.035 with VAT 15% -> 0.035 * 0.15 = 0.00525 -> rounds to 0.01
        var calc1 = taxEngine.calculateLineTax(
                BigDecimal.ONE, new BigDecimal("0.035"), BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, Instant.parse("2026-05-01T00:00:00Z")
        );
        assertNotNull(calc1);

        // Standard 100.005 gross value rounding
        var calc2 = taxEngine.calculateLineTax(
                new BigDecimal("1"), new BigDecimal("10.035"), BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, Instant.parse("2026-05-01T00:00:00Z")
        );
        // 10.035 rounds to 10.04 pre-tax, 10.04 * 0.15 = 1.506 -> 1.51 tax
        assertEquals(new BigDecimal("10.04"), calc2.preTaxValue());
        assertEquals(new BigDecimal("1.51"), calc2.taxAmount());
        assertEquals(new BigDecimal("11.55"), calc2.totalLineAmount());
    }

    @Test
    @DisplayName("Financial 8: Excessive fractional precision inputs are scaled correctly without floating-point artifacts")
    void test_ExcessivePrecision_ScaledCorrectly() {
        BigDecimal excessiveQuantity = new BigDecimal("3.1415926535");
        BigDecimal excessivePrice = new BigDecimal("99.9999999");
        var calc = taxEngine.calculateLineTax(
                excessiveQuantity, excessivePrice, BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, Instant.parse("2026-05-01T00:00:00Z")
        );
        assertEquals(2, calc.preTaxValue().scale());
        assertEquals(2, calc.taxAmount().scale());
        assertEquals(2, calc.totalLineAmount().scale());
    }

    @Test
    @DisplayName("Financial 9: Large invoice totals (billions) calculate without arithmetic overflow")
    void test_LargeNumbers_NoOverflow() {
        BigDecimal largeQty = new BigDecimal("1000000.00");
        BigDecimal largePrice = new BigDecimal("5000000.00"); // 5 Trillion ETB gross
        var calc = taxEngine.calculateLineTax(
                largeQty, largePrice, BigDecimal.ZERO,
                TaxCode.VAT15, BigDecimal.ZERO, Instant.parse("2026-05-01T00:00:00Z")
        );
        assertEquals(new BigDecimal("5000000000000.00"), calc.preTaxValue());
        assertEquals(new BigDecimal("750000000000.00"), calc.taxAmount());
        assertEquals(new BigDecimal("5750000000000.00"), calc.totalLineAmount());
    }
}
