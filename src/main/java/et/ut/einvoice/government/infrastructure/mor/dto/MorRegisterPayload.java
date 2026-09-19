package et.ut.einvoice.government.infrastructure.mor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record MorRegisterPayload(
        @JsonProperty("BuyerDetails") BuyerDetails buyerDetails,
        @JsonProperty("DocumentDetails") DocumentDetails documentDetails,
        @JsonProperty("ItemList") List<ItemDetails> itemList,
        @JsonProperty("PaymentDetails") PaymentDetails paymentDetails,
        @JsonProperty("ReferenceDetails") ReferenceDetails referenceDetails,
        @JsonProperty("SellerDetails") SellerDetails sellerDetails,
        @JsonProperty("SourceSystem") SourceSystem sourceSystem,
        @JsonProperty("TransactionType") String transactionType,
        @JsonProperty("ValueDetails") ValueDetails valueDetails,
        @JsonProperty("Version") String version
) {
    public record BuyerDetails(
            @JsonProperty("City") String city,
            @JsonProperty("Country") String country,
            @JsonProperty("Email") String email,
            @JsonProperty("HouseNumber") String houseNumber,
            @JsonProperty("IdNumber") String idNumber,
            @JsonProperty("IdType") String idType,
            @JsonProperty("Kebele") String kebele,
            @JsonProperty("LegalName") String legalName,
            @JsonProperty("Phone") String phone,
            @JsonProperty("Region") String region,
            @JsonProperty("Tin") String tin,
            @JsonProperty("VatNumber") String vatNumber,
            @JsonProperty("Wereda") String wereda,
            @JsonProperty("Zone") String zone
    ) {}

    public record DocumentDetails(
            @JsonProperty("DocumentNumber") String documentNumber,
            @JsonProperty("Date") String date,
            @JsonProperty("Type") String type
    ) {}

    public record ItemDetails(
            @JsonProperty("LineNumber") Integer lineNumber,
            @JsonProperty("ItemCode") String itemCode,
            @JsonProperty("ProductDescription") String productDescription,
            @JsonProperty("NatureOfSupplies") String natureOfSupplies,
            @JsonProperty("Unit") String unit,
            @JsonProperty("Quantity") BigDecimal quantity,
            @JsonProperty("UnitPrice") BigDecimal unitPrice,
            @JsonProperty("PreTaxValue") BigDecimal preTaxValue,
            @JsonProperty("TaxCode") String taxCode,
            @JsonProperty("TaxAmount") BigDecimal taxAmount,
            @JsonProperty("Discount") BigDecimal discount,
            @JsonProperty("ExciseTaxValue") BigDecimal exciseTaxValue,
            @JsonProperty("HarmonizationCode") String harmonizationCode,
            @JsonProperty("TotalLineAmount") BigDecimal totalLineAmount
    ) {}

    public record PaymentDetails(
            @JsonProperty("Mode") String mode,
            @JsonProperty("PaymentTerm") String paymentTerm
    ) {}

    public record ReferenceDetails(
            @JsonProperty("PreviousIrn") String previousIrn,
            @JsonProperty("RelatedDocument") String relatedDocument
    ) {}

    public record SellerDetails(
            @JsonProperty("City") String city,
            @JsonProperty("Email") String email,
            @JsonProperty("HouseNumber") String houseNumber,
            @JsonProperty("LegalName") String legalName,
            @JsonProperty("Locality") String locality,
            @JsonProperty("Phone") String phone,
            @JsonProperty("Region") String region,
            @JsonProperty("SubCity") String subCity,
            @JsonProperty("Tin") String tin,
            @JsonProperty("VatNumber") String vatNumber,
            @JsonProperty("Wereda") String wereda
    ) {}

    public record SourceSystem(
            @JsonProperty("CashierName") String cashierName,
            @JsonProperty("InvoiceCounter") Long invoiceCounter,
            @JsonProperty("SalesPersonName") String salesPersonName,
            @JsonProperty("SystemNumber") String systemNumber,
            @JsonProperty("SystemType") String systemType
    ) {}

    public record ValueDetails(
            @JsonProperty("Discount") BigDecimal discount,
            @JsonProperty("ExciseValue") BigDecimal exciseValue,
            @JsonProperty("IncomeWithholdValue") BigDecimal incomeWithholdValue,
            @JsonProperty("TaxValue") BigDecimal taxValue,
            @JsonProperty("TotalValue") BigDecimal totalValue,
            @JsonProperty("TransactionWithholdValue") BigDecimal transactionWithholdValue,
            @JsonProperty("InvoiceCurrency") String invoiceCurrency
    ) {}
}
