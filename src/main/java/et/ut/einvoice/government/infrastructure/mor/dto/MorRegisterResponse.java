package et.ut.einvoice.government.infrastructure.mor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MorRegisterResponse(
        int statusCode,
        String message,
        JsonNode body
) {
    public boolean isSuccessful() {
        return statusCode == 200 && body != null && body.has("irn");
    }

    public String getIrn() {
        return (body != null && body.has("irn")) ? body.get("irn").asText() : null;
    }

    public String getAckDate() {
        return (body != null && body.has("ackDate")) ? body.get("ackDate").asText() : null;
    }

    public String getSignedQr() {
        return (body != null && body.has("signedQR")) ? body.get("signedQR").asText() : null;
    }

    public String getSignedInvoice() {
        return (body != null && body.has("signedInvoice")) ? body.get("signedInvoice").asText() : null;
    }
}
