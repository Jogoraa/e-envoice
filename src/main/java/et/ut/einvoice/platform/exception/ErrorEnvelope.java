package et.ut.einvoice.platform.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API Error Envelope providing structured, machine-readable error responses.
 * Strictly prevents information disclosure (no stack traces, SQL syntax, or internal class names).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
        Integer status,
        String code,
        String message,
        String localizedMessage,
        String correlationId,
        String requestId,
        Instant timestamp,
        String path,
        List<ValidationErrorDetail> details
) {
    @JsonProperty("error")
    public String getError() {
        return code;
    }

    public record ValidationErrorDetail(
            String field,
            String issue,
            Object rejectedValue
    ) {}

    public static ErrorEnvelope of(String code, String message, String localizedMessage, String correlationId) {
        return new ErrorEnvelope(null, code, message, localizedMessage, correlationId, correlationId, Instant.now(), null, null);
    }

    public static ErrorEnvelope of(String code, String message, String localizedMessage, String correlationId, List<ValidationErrorDetail> details) {
        return new ErrorEnvelope(null, code, message, localizedMessage, correlationId, correlationId, Instant.now(), null, details);
    }

    public static ErrorEnvelope of(int status, String code, String message, String localizedMessage, String correlationId, String path) {
        return new ErrorEnvelope(status, code, message, localizedMessage, correlationId, correlationId, Instant.now(), path, null);
    }

    public static ErrorEnvelope of(int status, String code, String message, String localizedMessage, String correlationId, String path, List<ValidationErrorDetail> details) {
        return new ErrorEnvelope(status, code, message, localizedMessage, correlationId, correlationId, Instant.now(), path, details);
    }
}
