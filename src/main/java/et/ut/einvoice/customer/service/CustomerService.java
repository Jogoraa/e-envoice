package et.ut.einvoice.customer.service;

import et.ut.einvoice.customer.domain.Customer;
import et.ut.einvoice.customer.dto.CreateOrUpdateCustomerRequest;
import et.ut.einvoice.customer.dto.CustomerDto;
import et.ut.einvoice.customer.repository.CustomerRepository;
import et.ut.einvoice.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> searchCustomers(UUID tenantId, String query, Pageable pageable) {
        String effectiveQuery = (query != null) ? query.trim() : "";
        return customerRepository.searchCustomers(tenantId, effectiveQuery, pageable)
                .map(CustomerDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> getAllActiveCustomers(UUID tenantId) {
        return customerRepository.findByTenantIdAndStatusOrderByLegalNameAsc(tenantId, "ACTIVE")
                .stream()
                .map(CustomerDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomerById(UUID tenantId, UUID customerId) {
        Customer c = customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer record not found for this tenant organization.",
                        "የደንበኛ መረጃ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));
        return CustomerDto.fromEntity(c);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerDto> lookupByTin(UUID tenantId, String tin) {
        if (tin == null || tin.trim().isBlank()) return Optional.empty();
        return customerRepository.findByTenantIdAndTin(tenantId, tin.trim())
                .map(CustomerDto::fromEntity);
    }

    @Transactional
    public CustomerDto createCustomer(UUID tenantId, CreateOrUpdateCustomerRequest request) {
        String cleanTin = request.normalizedTin();

        if (cleanTin != null && !cleanTin.isBlank()) {
            Optional<Customer> existing = customerRepository.findByTenantIdAndTin(tenantId, cleanTin);
            if (existing.isPresent()) {
                throw new BusinessException(
                        "DUPLICATE_CUSTOMER_TIN",
                        "A customer with TIN " + cleanTin + " is already registered in your organization.",
                        "ይህ የግብር ከፋይ መለያ ቁጥር (TIN) ያለው ደንበኛ አስቀድሞ ተመዝግቧል።",
                        HttpStatus.CONFLICT
                );
            }
        }

        Customer customer = new Customer(UUID.randomUUID(), tenantId, request.legalName().trim());
        applyRequestFields(customer, request);

        Customer saved = customerRepository.save(customer);
        log.info("Registered new customer master entity {} [{}] for tenant {}", saved.getId(), saved.getLegalName(), tenantId);
        return CustomerDto.fromEntity(saved);
    }

    @Transactional
    public CustomerDto updateCustomer(UUID tenantId, UUID customerId, CreateOrUpdateCustomerRequest request) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new BusinessException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer record not found for this tenant organization.",
                        "የደንበኛ መረጃ አልተገኘም።",
                        HttpStatus.NOT_FOUND
                ));

        String cleanTin = request.normalizedTin();
        if (cleanTin != null && !cleanTin.isBlank()) {
            Optional<Customer> existing = customerRepository.findByTenantIdAndTin(tenantId, cleanTin);
            if (existing.isPresent() && !existing.get().getId().equals(customerId)) {
                throw new BusinessException(
                        "DUPLICATE_CUSTOMER_TIN",
                        "A different customer with TIN " + cleanTin + " is already registered.",
                        "ይህ የግብር ከፋይ መለያ ቁጥር (TIN) ያለው ሌላ ደንበኛ አስቀድሞ ተመዝግቧል።",
                        HttpStatus.CONFLICT
                );
            }
        }

        applyRequestFields(customer, request);
        customer.setUpdatedAt(Instant.now());

        Customer saved = customerRepository.save(customer);
        log.info("Updated customer master entity {} [{}] for tenant {}", saved.getId(), saved.getLegalName(), tenantId);
        return CustomerDto.fromEntity(saved);
    }

    @Transactional
    public CustomerDto upsertFromInvoiceBuyer(
            UUID tenantId,
            UUID branchId,
            String legalName,
            String tin,
            String vatNumber,
            String phone,
            String email,
            String country,
            String region,
            String city,
            String zone,
            String woreda,
            String kebele,
            String houseNumber,
            String idType,
            String idNumber
    ) {
        if (legalName == null || legalName.trim().isBlank()) {
            return null;
        }

        String cleanTin = (tin != null && !tin.trim().isBlank()) ? tin.trim() : null;

        Customer customer = null;
        if (cleanTin != null) {
            customer = customerRepository.findByTenantIdAndTin(tenantId, cleanTin).orElse(null);
        }

        if (customer == null) {
            customer = new Customer(UUID.randomUUID(), tenantId, legalName.trim());
        }

        customer.setBranchId(branchId);
        customer.setLegalName(legalName.trim());
        if (cleanTin != null) customer.setTin(cleanTin);
        if (vatNumber != null) customer.setVatNumber(vatNumber.trim());
        if (phone != null) customer.setPhone(phone.trim());
        if (email != null) customer.setEmail(email.trim());
        if (country != null) customer.setCountry(country.trim());
        if (region != null) customer.setRegion(region.trim());
        if (city != null) customer.setCity(city.trim());
        if (zone != null) customer.setZone(zone.trim());
        if (woreda != null) customer.setWoreda(woreda.trim());
        if (kebele != null) customer.setKebele(kebele.trim());
        if (houseNumber != null) customer.setHouseNumber(houseNumber.trim());
        if (idType != null) customer.setBuyerIdType(idType.trim());
        if (idNumber != null) customer.setBuyerIdNumber(idNumber.trim());
        customer.setUpdatedAt(Instant.now());

        Customer saved = customerRepository.save(customer);
        return CustomerDto.fromEntity(saved);
    }

    private void applyRequestFields(Customer customer, CreateOrUpdateCustomerRequest req) {
        customer.setLegalName(req.legalName().trim());
        customer.setTin(req.normalizedTin());
        customer.setVatNumber(req.vatNumber() != null ? req.vatNumber().trim() : null);
        customer.setTradeName(req.tradeName() != null ? req.tradeName().trim() : null);
        customer.setPhone(req.phone() != null ? req.phone().trim() : null);
        customer.setEmail(req.email() != null ? req.email().trim() : null);
        customer.setCountry(req.country() != null && !req.country().isBlank() ? req.country().trim() : "ET");
        customer.setRegion(req.region() != null ? req.region().trim() : null);
        customer.setCity(req.city() != null ? req.city().trim() : null);
        customer.setZone(req.zone() != null ? req.zone().trim() : null);
        customer.setWoreda(req.woreda() != null ? req.woreda().trim() : null);
        customer.setKebele(req.kebele() != null ? req.kebele().trim() : null);
        customer.setHouseNumber(req.houseNumber() != null ? req.houseNumber().trim() : null);
        customer.setBuyerIdType(req.buyerIdType() != null && !req.buyerIdType().isBlank() ? req.buyerIdType().trim() : "TIN");
        customer.setBuyerIdNumber(req.buyerIdNumber() != null ? req.buyerIdNumber().trim() : null);
        customer.setIsVatRegistered(req.isVatRegistered() != null ? req.isVatRegistered() : false);
        if (req.branchId() != null) customer.setBranchId(req.branchId());
    }
}
