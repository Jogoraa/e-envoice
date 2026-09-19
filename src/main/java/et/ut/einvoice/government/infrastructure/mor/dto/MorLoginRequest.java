package et.ut.einvoice.government.infrastructure.mor.dto;

public record MorLoginRequest(
        String clientId,
        String clientSecret,
        String apikey,
        String tin
) {}
