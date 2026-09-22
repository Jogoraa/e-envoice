package et.ut.einvoice.government.infrastructure.mor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.government.infrastructure.mor.dto.MorLoginRequest;
import et.ut.einvoice.government.infrastructure.mor.dto.MorLoginResponse;
import et.ut.einvoice.government.infrastructure.mor.dto.MorRegisterPayload;
import et.ut.einvoice.government.service.MorInvoiceCanonicalizationService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MorEirsRegistrationProvider implements GovernmentRegistrationProvider {

    private static final Logger log = LoggerFactory.getLogger(MorEirsRegistrationProvider.class);
    private static final Pattern EXPECTED_SEQ_PATTERN = Pattern.compile("expected\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MorInvoiceCanonicalizationService canonicalizationService;
    private final String baseUrl;
    private final String configuredClientId;
    private final String configuredClientSecret;
    private final String configuredApiKey;
    private final String configuredSellerTin;
    private final String defaultSystemNumber;
    private final String defaultSystemType;

    private static class CachedToken {
        final String token;
        final Instant expiresAt;

        CachedToken(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return token != null && !token.isBlank() && Instant.now().plusSeconds(60).isBefore(expiresAt);
        }
    }

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>(null);
    private final ReentrantLock authLock = new ReentrantLock();

    public MorEirsRegistrationProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            MorInvoiceCanonicalizationService canonicalizationService,
            @Value("${mor.gateway.base-url:${MOR_GATEWAY_URL:http://core.mor.gov.et}}") String baseUrl,
            @Value("${mor.gateway.client-id:${MOR_CLIENT_ID:}}") String configuredClientId,
            @Value("${mor.gateway.client-secret:${MOR_CLIENT_SECRET:}}") String configuredClientSecret,
            @Value("${mor.gateway.api-key:${MOR_API_KEY:}}") String configuredApiKey,
            @Value("${mor.gateway.seller-tin:${MOR_SELLER_TIN:}}") String configuredSellerTin,
            @Value("${mor.gateway.system-number:${MOR_SYSTEM_NUMBER:}}") String defaultSystemNumber,
            @Value("${mor.gateway.system-type:${MOR_SYSTEM_TYPE:POS}}") String defaultSystemType
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.canonicalizationService = canonicalizationService;
        this.baseUrl = baseUrl;
        this.configuredClientId = configuredClientId;
        this.configuredClientSecret = configuredClientSecret;
        this.configuredApiKey = configuredApiKey;
        this.configuredSellerTin = configuredSellerTin;
        this.defaultSystemNumber = defaultSystemNumber;
        this.defaultSystemType = defaultSystemType;
    }

    @Override
    public String getProviderVersion() {
        return "EIRS-v1.0";
    }

    public String getOrAuthenticateToken(String tin) {
        CachedToken current = tokenCache.get();
        if (current != null && current.isValid()) {
            return current.token;
        }

        authLock.lock();
        try {
            current = tokenCache.get();
            if (current != null && current.isValid()) {
                return current.token;
            }

            if (configuredClientId.isBlank() || configuredClientSecret.isBlank() || configuredApiKey.isBlank()) {
                log.debug("MoR credentials not present in environment; proceeding with caller token if available");
                return null;
            }

            String effectiveTin = (configuredSellerTin != null && !configuredSellerTin.isBlank())
                    ? configuredSellerTin
                    : ((tin != null && !tin.isBlank() && !"9000000000".equals(tin)) ? tin : "0041746204");

            String token = authenticate(configuredClientId, configuredClientSecret, configuredApiKey, effectiveTin);
            tokenCache.set(new CachedToken(token, Instant.now().plusSeconds(3500)));
            return token;
        } catch (Exception e) {
            log.warn("Automatic token retrieval failed: {}", e.getMessage());
            return null;
        } finally {
            authLock.unlock();
        }
    }

    @Override
    public String authenticate(String clientId, String clientSecret, String apiKey, String tin) {
        try {
            log.info("Authenticating with MoR Gateway at {}", baseUrl);
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
                String token = resp.data().accessToken();
                Long expiresIn = resp.data().expiresIn() != null ? resp.data().expiresIn() : 3600L;
                tokenCache.set(new CachedToken(token, Instant.now().plusSeconds(Math.max(60, expiresIn - 60))));
                log.info("Successfully authenticated with MoR Gateway (expiresIn={}s)", expiresIn);
                return token;
            }
            throw new RuntimeException("Authentication response missing accessToken");
        } catch (Exception ex) {
            log.error("Failed to authenticate with MoR Gateway at {}: {}", baseUrl, ex.getMessage());
            throw new RuntimeException("MoR Gateway authentication failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GovernmentRegistrationResult registerInvoice(Invoice invoice, TaxpayerProfile sellerProfile, String token) {
        String effectiveToken = token;
        if (effectiveToken == null || effectiveToken.isBlank() || "bearer-token".equalsIgnoreCase(effectiveToken) || "AUTO_AUTH".equalsIgnoreCase(effectiveToken) || !effectiveToken.contains(".")) {
            effectiveToken = getOrAuthenticateToken(sellerProfile != null ? sellerProfile.getTin() : configuredSellerTin);
        }

        try {
            MorRegisterPayload payload = canonicalizationService.buildGovernmentPayload(
                    invoice, sellerProfile, defaultSystemNumber, defaultSystemType
            );
            log.info("Submitting Invoice [Doc: {}, Counter: {}] to MoR Gateway...", invoice.getDocumentNumber(), invoice.getInvoiceCounter());

            var requestSpec = webClient.post()
                    .uri("/v1/register")
                    .contentType(MediaType.APPLICATION_JSON);

            if (effectiveToken != null && !effectiveToken.isBlank()) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + effectiveToken);
            }

            String responseString = requestSpec
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
            if (ex.getStatusCode().value() == 401) {
                tokenCache.set(null); // Invalidate token on 401
            }
            // Check if 400 has sequence mismatch error
            try {
                JsonNode errRoot = objectMapper.readTree(ex.getResponseBodyAsString());
                if (errRoot.has("body") && errRoot.get("body").isArray()) {
                    Long expectedDoc = null;
                    Long expectedCounter = null;
                    for (JsonNode err : errRoot.get("body")) {
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
                        log.warn("MoR Gateway 400 requested sequence adjustment: nextDoc={}, nextCounter={}", expectedDoc, expectedCounter);
                        return GovernmentRegistrationResult.sequenceAdjustment(expectedDoc, expectedCounter);
                    }
                }
            } catch (Exception ignored) {}

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
            String effectiveToken = token != null && !token.isBlank() && !"bearer-token".equalsIgnoreCase(token)
                    ? token : getOrAuthenticateToken(sellerProfile.getTin());

            var requestSpec = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/verify")
                            .queryParam("documentNumber", documentNumber)
                            .queryParam("tin", sellerProfile.getTin())
                            .build());

            if (effectiveToken != null && !effectiveToken.isBlank()) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + effectiveToken);
            }

            String responseString = requestSpec
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

            var requestSpec = webClient.post()
                    .uri("/v1/cancel")
                    .contentType(MediaType.APPLICATION_JSON);

            if (token != null && !token.isBlank() && !"bearer-token".equalsIgnoreCase(token)) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }

            String responseString = requestSpec
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
}
