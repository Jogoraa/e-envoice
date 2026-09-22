package et.ut.einvoice.customer.dto;

import et.ut.einvoice.customer.domain.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CustomerDto(
        UUID id,
        UUID tenantId,
        UUID branchId,
        String tin,
        String vatNumber,
        String legalName,
        String tradeName,
        String phone,
        String email,
        String country,
        String region,
        String city,
        String zone,
        String woreda,
        String kebele,
        String houseNumber,
        String buyerIdType,
        String buyerIdNumber,
        Boolean isVatRegistered,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerDto fromEntity(Customer c) {
        return new CustomerDto(
                c.getId(),
                c.getTenantId(),
                c.getBranchId(),
                c.getTin(),
                c.getVatNumber(),
                c.getLegalName(),
                c.getTradeName(),
                c.getPhone(),
                c.getEmail(),
                c.getCountry(),
                c.getRegion(),
                c.getCity(),
                c.getZone(),
                c.getWoreda(),
                c.getKebele(),
                c.getHouseNumber(),
                c.getBuyerIdType(),
                c.getBuyerIdNumber(),
                c.getIsVatRegistered(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
