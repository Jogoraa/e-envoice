package et.ut.einvoice.government.infrastructure.mor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.infrastructure.mor.dto.MorLoginRequest;
import et.ut.einvoice.government.infrastructure.mor.dto.MorLoginResponse;
import et.ut.einvoice.government.infrastructure.mor.dto.MorRegisterPayload;
import et.ut.einvoice.government.infrastructure.mor.dto.MorRegisterResponse;
import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MorEirsRegistrationProvider implements GovernmentRegistrationProvider {

    private static final Logger log = LoggerFactory.getLogger(MorEirsRegistrationProvider.class);
    private static final Pattern EXPECTED_SEQ_PATTERN = Pattern.compile("expected\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter MOR_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm:ss");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public MorEirsRegistrationProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${mor.gateway.base-url:http://core.mor.gov.et}") String baseUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getProviderVersion() {
        return "EIRS-v1.0";
    }

    @Override
    public String authenticate(String clientId, String clientSecret, String apiKey, String tin) {
        try {
            var req = new MorLoginRequest(clientId, clientSecret, apiKey, tin);
            var resp = webClient.post()
                    .uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(MorLoginResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (resp != null && resp.data() != null && resp.data().accessToken() != null) {
                return resp.data().accessToken();
            }
            throw new RuntimeException("Authentication response missing accessToken");
        } catch (Exception ex) {
            log.error("Failed to authenticate with MoR Gateway at {}", baseUrl, ex);
            throw new RuntimeException("MoR Gateway authentication failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GovernmentRegistrationResult registerInvoice(Invoice invoice, TaxpayerProfile sellerProfile, String token) {
        try {
            MorRegisterPayload payload = buildPayload(invoice, sellerProfile);
            log.info("Submitting Invoice [Doc: {}, Counter: {}] to MoR Gateway...", invoice.getDocumentNumber(), invoice.getInvoiceCounter());

            String responseString = webClient.post()
                    .uri("/v1/register")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode rootNode = objectMapper.readTree(responseString);
            int statusCode = rootNode.has("statusCode") ? rootNode.get("statusCode").asInt() : 200;

            if (statusCode == 200 && rootNode.has("body") && rootNode.get("body").has("irn")) {
                JsonNode body = rootNode.get("body");
                String irn = body.get("irn").asText();
                String rrn = body.has("documentNumber") ? "RRN-" + body.get("documentNumber").asText() : "RRN-" + invoice.getDocumentNumber();
                String ackDate = body.has("ackDate") ? body.get("ackDate").asText() : "";
                String signedQr = body.has("signedQR") ? body.get("signedQR").asText() : "";
                String signedInvoice = body.has("signedInvoice") ? body.get("signedInvoice").asText() : "";

                log.info("Invoice registered successfully with MoR! IRN: {}", irn);
                return GovernmentRegistrationResult.success(irn, rrn, ackDate, signedQr, signedInvoice);
            }

            // Sequence mismatch handling (auto-sync)
            if (rootNode.has("body") && rootNode.get("body").isArray()) {
                Long expectedDoc = null;
                Long expectedCounter = null;

                for (JsonNode err : rootNode.get("body")) {
                    String portion = err.has("portion") ? err.get("portion").asText() : "";
                    String msg = err.has("errorMessage") ? err.get("errorMessage").toString() : "";
                    Matcher m = EXPECTED_SEQ_PATTERN.matcher(msg);
                    if (m.find()) {
                        long val = Long.parseLong(m.group(1));
                        if ("DocumentDetails".equalsIgnoreCase(portion)) expectedDoc = val;
                        if ("SourceSystem".equalsIgnoreCase(portion)) expectedCounter = val;
                    }
                }

                if (expectedDoc != null || expectedCounter != null) {
                    log.warn("MoR Gateway requested sequence adjustment: nextDoc={}, nextCounter={}", expectedDoc, expectedCounter);
                    return GovernmentRegistrationResult.sequenceAdjustment(expectedDoc, expectedCounter);
                }
            }

            return GovernmentRegistrationResult.failure("REGISTRATION_REJECTED", rootNode.toString());

        } catch (WebClientResponseException ex) {
            log.error("MoR Gateway returned HTTP error status: {}", ex.getStatusCode());
            return GovernmentRegistrationResult.failure("MOR_HTTP_" + ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Network or Gateway communication failure connecting to MoR: {}", ex.getMessage());
            return GovernmentRegistrationResult.failure("GATEWAY_CONNECTION_ERROR", ex.getMessage());
        }
    }

    @Override
    public GovernmentVerificationResult verifySubmission(String submissionId, String documentNumber, TaxpayerProfile sellerProfile, String token) {
        try {
            log.info("Verifying submission with MoR Gateway: submissionId={}, doc={}", submissionId, documentNumber);
            String responseString = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/verify")
                            .queryParam("documentNumber", documentNumber)
                            .queryParam("tin", sellerProfile.getTin())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode rootNode = objectMapper.readTree(responseString);
            if (rootNode.has("body") && rootNode.get("body").has("irn")) {
                JsonNode body = rootNode.get("body");
                String irn = body.get("irn").asText();
                String rrn = body.has("documentNumber") ? "RRN-" + body.get("documentNumber").asText() : "RRN-" + documentNumber;
                String ackDate = body.has("ackDate") ? body.get("ackDate").asText() : "";
                return GovernmentVerificationResult.found(irn, rrn, ackDate);
            }
            return GovernmentVerificationResult.notFound();
        } catch (Exception ex) {
            log.warn("Submission verification query failed for submissionId {}: {}", submissionId, ex.getMessage());
            return GovernmentVerificationResult.notFound();
        }
    }

    @Override
    public CancellationResult cancelInvoice(String irn, String reason, String token) {
        try {
            var body = objectMapper.createObjectNode();
            body.put("irn", irn);
            body.put("reason", reason);

            String responseString = webClient.post()
                    .uri("/v1/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.info("Cancellation response from MoR: {}", responseString);
            return new CancellationResult(true, "CANCEL-" + irn.substring(0, Math.min(12, irn.length())), "Invoice successfully cancelled in MoR");
        } catch (Exception ex) {
            log.error("Cancellation request failed with MoR for IRN {}: {}", irn, ex.getMessage());
            return new CancellationResult(false, null, ex.getMessage());
        }
    }

    private MorRegisterPayload buildPayload(Invoice inv, TaxpayerProfile seller) {
        String formattedDate = inv.getInvoiceDate().atZone(ZoneId.of("Africa/Addis_Ababa")).format(MOR_DATE_FORMAT);

        var buyerDetails = new MorRegisterPayload.BuyerDetails(
                "0",
                "70",
                inv.getBuyerEmail(),
                null,
                inv.getBuyerIdNumber() != null ? inv.getBuyerIdNumber() : "11122222222222222",
                inv.getBuyerIdType() != null ? inv.getBuyerIdType() : "KID",
                "01",
                inv.getBuyerLegalName() != null ? inv.getBuyerLegalName() : "Walk-in Customer",
                inv.getBuyerPhone() != null ? inv.getBuyerPhone() : "0911000000",
                inv.getBuyerRegion() != null ? inv.getBuyerRegion() : "13",
                inv.getBuyerTin(),
                null,
                inv.getBuyerWoreda() != null ? inv.getBuyerWoreda() : "01",
                "SHA"
        );

        var documentDetails = new MorRegisterPayload.DocumentDetails(
                inv.getDocumentNumber(),
                formattedDate,
                "INV"
        );

        List<MorRegisterPayload.ItemDetails> itemList = new ArrayList<>();
        for (var line : inv.getLines()) {
            itemList.add(new MorRegisterPayload.ItemDetails(
                    line.getLineNumber(),
                    line.getItemCode(),
                    line.getProductDescription(),
                    line.getNatureOfSupplies(),
                    line.getUnit(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getPreTaxValue(),
                    line.getTaxCode(),
                    line.getTaxAmount(),
                    line.getDiscount(),
                    line.getExciseTaxValue(),
                    null,
                    line.getTotalLineAmount()
            ));
        }

        var paymentDetails = new MorRegisterPayload.PaymentDetails(
                inv.getPaymentMode(),
                inv.getPaymentTerm()
        );

        var referenceDetails = new MorRegisterPayload.ReferenceDetails(
                inv.getPreviousIrn() != null ? inv.getPreviousIrn() : "",
                null
        );

        var sellerDetails = new MorRegisterPayload.SellerDetails(
                null,
                seller.getEmail(),
                null,
                seller.getLegalName(),
                null,
                seller.getPhone(),
                seller.getRegion(),
                null,
                seller.getTin(),
                seller.getVatNumber(),
                seller.getWoreda()
        );

        var sourceSystem = new MorRegisterPayload.SourceSystem(
                "Cashier",
                inv.getInvoiceCounter(),
                "Sales Officer",
                seller.getSystemNumber(),
                seller.getSystemType()
        );

        var valueDetails = new MorRegisterPayload.ValueDetails(
                BigDecimal.ZERO,
                inv.getExciseTotal(),
                BigDecimal.ZERO,
                inv.getTaxTotal(),
                inv.getGrandTotal(),
                BigDecimal.ZERO,
                inv.getCurrency()
        );

        return new MorRegisterPayload(
                buyerDetails,
                documentDetails,
                itemList,
                paymentDetails,
                referenceDetails,
                sellerDetails,
                sourceSystem,
                inv.getTransactionType().name(),
                valueDetails,
                "1"
        );
    }
}
