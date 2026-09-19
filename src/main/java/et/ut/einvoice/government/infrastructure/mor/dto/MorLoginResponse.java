package et.ut.einvoice.government.infrastructure.mor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MorLoginResponse(
        AuthData data,
        String status
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthData(
            String accessToken,
            String refreshToken,
            String encryptionKey,
            Long expiresIn
    ) {}
}
