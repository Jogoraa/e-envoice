package et.ut.einvoice.compliance.dto;

import et.ut.einvoice.customer.domain.Customer;
import java.util.UUID;

public record AuthorityCustomerResponseDto(
    UUID id,
    UUID tenantId,
    String tin,
    String legalName,
    String tradeName,
    String phone,
    String email,
    String city,
    String woreda,
    String kebele,
    String status
) {
    public static AuthorityCustomerResponseDto fromEntity(Customer c) {
        return new AuthorityCustomerResponseDto(
                c.getId(),
                c.getTenantId(),
                c.getTin(),
                c.getLegalName(),
                c.getTradeName(),
                c.getPhone(),
                c.getEmail(),
                c.getCity(),
                c.getWoreda(),
                c.getKebele(),
                c.getStatus()
        );
    }
}
