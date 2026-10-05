package et.ut.einvoice.offline.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequestOfflineAllocationDto(
        @NotNull UUID deviceId,
        long blockSize,
        Long validityDays,
        String authorityRef
) {}
