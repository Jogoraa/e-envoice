package et.ut.einvoice.taxation.service;

import et.ut.einvoice.platform.exception.BusinessException;
import et.ut.einvoice.taxation.domain.TaxBreakdown;
import et.ut.einvoice.taxation.domain.TaxCode;
import et.ut.einvoice.taxation.domain.TaxRule;
import et.ut.einvoice.taxation.repository.TaxRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Independent, versioned tax calculation engine for Ethiopian tax compliance (Directive No. 1142/2026).
 */
@Service
public class TaxEngine {

    private final TaxRuleRepository taxRuleRepository;

    @Autowired
    public TaxEngine(TaxRuleRepository taxRuleRepository) {
        this.taxRuleRepository = taxRuleRepository;
    }

    public LineTaxCalculation calculateLineTax(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount, TaxCode taxCode, BigDecimal exciseRate) {
        return calculateLineTax(quantity, unitPrice, discount, taxCode, exciseRate, Instant.now());
    }

    public LineTaxCalculation calculateLineTax(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount, TaxCode taxCode, BigDecimal exciseRate, Instant effectiveDate) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Line item quantity must be strictly greater than zero.", HttpStatus.BAD_REQUEST);
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_UNIT_PRICE", "Line item unit price cannot be negative.", HttpStatus.BAD_REQUEST);
        }
        if (discount == null) {
            discount = BigDecimal.ZERO;
        } else if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Line item discount cannot be negative.", HttpStatus.BAD_REQUEST);
        }
        if (effectiveDate == null) {
            effectiveDate = Instant.now();
        }
        if (taxCode == null) {
            taxCode = TaxCode.VAT15;
        }

        // 1. Resolve rate & version from versioned tax rules (or fallback to default enum)
        BigDecimal effectiveRate = taxCode.getRate();
        int ruleVersion = 1;

        if (taxRuleRepository != null) {
            List<TaxRule> rules = taxRuleRepository.findActiveRules(taxCode.name(), effectiveDate);
            if (rules.size() > 1) {
                throw new BusinessException(
                        "TAX_RULE_OVERLAP_AMBIGUITY",
                        "Multiple overlapping active tax rules found for code " + taxCode.name() + " on date " + effectiveDate,
                        "ለተጠቀሰው የታክስ ኮድ በርካታ የተደራረቡ ንቁ የታክስ ህጎች ተገኝተዋል፤ እባክዎ የታክስ አስተዳዳሪውን ያነጋግሩ።",
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            if (rules.size() == 1) {
                TaxRule rule = rules.get(0);
                effectiveRate = rule.getRate();
                ruleVersion = rule.getVersion();
            }
        }

        BigDecimal grossValue = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(grossValue) > 0) {
            throw new BusinessException(
                    "DISCOUNT_EXCEEDS_SUBTOTAL",
                    "Line item discount (" + discount + ") cannot exceed gross line value (" + grossValue + ").",
                    HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal preTaxValue = grossValue.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal exciseAmount = BigDecimal.ZERO;
        if (exciseRate != null) {
            if (exciseRate.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("INVALID_EXCISE_RATE", "Excise tax rate cannot be negative.", HttpStatus.BAD_REQUEST);
            }
            if (exciseRate.compareTo(BigDecimal.ZERO) > 0) {
                exciseAmount = preTaxValue.multiply(exciseRate).setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal taxBase = preTaxValue.add(exciseAmount);
        BigDecimal taxAmount = taxBase.multiply(effectiveRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalLineAmount = preTaxValue.add(exciseAmount).add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        TaxBreakdown breakdown = new TaxBreakdown(taxCode, preTaxValue, effectiveRate, taxAmount, exciseAmount);
        return new LineTaxCalculation(preTaxValue, exciseAmount, taxAmount, totalLineAmount, breakdown, ruleVersion);
    }

    public record LineTaxCalculation(
            BigDecimal preTaxValue,
            BigDecimal exciseAmount,
            BigDecimal taxAmount,
            BigDecimal totalLineAmount,
            TaxBreakdown breakdown,
            int ruleVersion
    ) {
        public LineTaxCalculation(BigDecimal preTaxValue, BigDecimal exciseAmount, BigDecimal taxAmount, BigDecimal totalLineAmount, TaxBreakdown breakdown) {
            this(preTaxValue, exciseAmount, taxAmount, totalLineAmount, breakdown, 1);
        }
    }
}
