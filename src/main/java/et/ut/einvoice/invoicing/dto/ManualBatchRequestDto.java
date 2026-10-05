package et.ut.einvoice.invoicing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ManualBatchRequestDto(
        @NotEmpty List<@Valid CreateManualFiscalDocumentDto> documents
) {}
