package et.ut.einvoice.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrUpdateCustomerRequest(
        UUID branchId,

        @Pattern(regexp = "^$|^\\d{10}$", message = "TIN must contain exactly 10 numeric digits if provided.")
        @Size(max = 16)
        String tin,

        @Pattern(regexp = "^$|^[0-9a-zA-Z]{6,32}$", message = "VAT number must contain 6 to 32 alphanumeric characters.")
        @Size(max = 32)
        String vatNumber,

        @NotBlank(message = "Customer legal name is mandatory.")
        @Size(max = 255)
        String legalName,

        @Size(max = 255)
        String tradeName,

        @Pattern(regexp = "^$|^(\\+251|0)(9|7)\\d{8}$|^(\\+251|0)[1-5]\\d{7,8}$", message = "Invalid Ethiopian phone format. Expected 09XXXXXXXX, 07XXXXXXXX, or +2519XXXXXXXX.")
        @Size(max = 32)
        String phone,

        @Pattern(regexp = "^$|^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email address format.")
        @Size(max = 128)
        String email,

        @Size(max = 8)
        String country,

        @Size(max = 32)
        String region,

        @Size(max = 64)
        String city,

        @Size(max = 64)
        String zone,

        @Size(max = 32)
        String woreda,

        @Size(max = 32)
        String kebele,

        @Size(max = 32)
        String houseNumber,

        @Size(max = 32)
        String buyerIdType,

        @Size(max = 64)
        String buyerIdNumber,

        Boolean isVatRegistered
) {
    public String normalizedTin() {
        if (tin == null || tin.trim().isBlank()) return null;
        return tin.trim();
    }
}
