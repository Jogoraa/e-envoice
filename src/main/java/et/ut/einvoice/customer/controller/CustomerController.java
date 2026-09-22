package et.ut.einvoice.customer.controller;

import et.ut.einvoice.customer.dto.CreateOrUpdateCustomerRequest;
import et.ut.einvoice.customer.dto.CustomerDto;
import et.ut.einvoice.customer.service.CustomerService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer Master Management", description = "Enterprise multi-tenant Customer Master lifecycle, autocomplete search, and TIN validation endpoints")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "Search customers with pagination and query filtering")
    public ResponseEntity<Page<CustomerDto>> searchCustomers(
            @RequestParam(name = "query", required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(customerService.searchCustomers(tenantId, query, pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all active customers for tenant")
    public ResponseEntity<List<CustomerDto>> getAllActiveCustomers() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(customerService.getAllActiveCustomers(tenantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer details by ID")
    public ResponseEntity<CustomerDto> getCustomerById(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(customerService.getCustomerById(tenantId, id));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lookup existing customer by Ethiopian TIN")
    public ResponseEntity<CustomerDto> lookupByTin(@RequestParam("tin") String tin) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return customerService.lookupByTin(tenantId, tin)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a new Customer Master record")
    public ResponseEntity<CustomerDto> createCustomer(@Valid @RequestBody CreateOrUpdateCustomerRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        CustomerDto created = customerService.createCustomer(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing Customer Master record")
    public ResponseEntity<CustomerDto> updateCustomer(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateOrUpdateCustomerRequest request
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(customerService.updateCustomer(tenantId, id, request));
    }
}
