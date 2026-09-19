package et.ut.einvoice.taxation.domain;

import java.math.BigDecimal;

public enum TaxCode {
    VAT15("VAT 15%", new BigDecimal("0.1500")),
    VAT0("VAT 0%", BigDecimal.ZERO),
    VATEX("VAT Exempt", BigDecimal.ZERO),
    TOT_2("Turnover Tax 2%", new BigDecimal("0.0200")),
    TOT_10("Turnover Tax 10%", new BigDecimal("0.1000"));

    private final String description;
    private final BigDecimal rate;

    TaxCode(String description, BigDecimal rate) {
        this.description = description;
        this.rate = rate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public static TaxCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return VAT15;
        }
        for (TaxCode tc : values()) {
            if (tc.name().equalsIgnoreCase(code.trim())) {
                return tc;
            }
        }
        return VAT15;
    }
}
