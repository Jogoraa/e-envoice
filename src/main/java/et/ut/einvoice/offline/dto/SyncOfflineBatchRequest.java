package et.ut.einvoice.offline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncOfflineBatchRequest(
        @NotNull(message = "Device ID is mandatory.")
        UUID deviceId,
        @NotEmpty(message = "Offline batch must contain at least one item.")
        @Size(max = 500, message = "Offline batch cannot exceed 500 items.")
        @Valid
        List<OfflineInvoiceItemDto> transactions
) {
    public record OfflineInvoiceItemDto(
            @NotNull Long offlineSeqNo,
            @NotNull Instant bufferedAt,
            @NotNull String payloadJson,
            @NotNull String deviceSignature
    ) {}
}
