package et.ut.einvoice.catalog.controller;

import et.ut.einvoice.catalog.dto.CreateProductRequest;
import et.ut.einvoice.catalog.dto.ProductDto;
import et.ut.einvoice.catalog.service.ProductService;
import et.ut.einvoice.platform.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/products")
@Tag(name = "Product Catalog Management", description = "Tenant-scoped physical product registration, SKU management, barcode lookup, and stock tracking")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Search products with pagination, category, and branch filtering")
    public ResponseEntity<Page<ProductDto>> searchProducts(
            @RequestParam(name = "branchId", required = false) UUID branchId,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "category", required = false) String categoryCode,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(productService.searchProducts(tenantId, branchId, query, categoryCode, isActive, pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all products for current tenant")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(productService.getAllProducts(tenantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductDto> getProductById(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(productService.getProductById(tenantId, id));
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Lookup product by barcode")
    public ResponseEntity<ProductDto> getProductByBarcode(@PathVariable("barcode") String barcode) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return productService.getProductByBarcode(tenantId, barcode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('CASHIER') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Register a new product in the tenant catalog")
    public ResponseEntity<ProductDto> registerProduct(@Valid @RequestBody CreateProductRequest request) {
        var ctx = TenantContextHolder.getRequiredContext();
        ProductDto created = productService.registerProduct(ctx.tenantId(), request, ctx.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Update an existing catalog product")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateProductRequest request
    ) {
        var ctx = TenantContextHolder.getRequiredContext();
        return ResponseEntity.ok(productService.updateProduct(ctx.tenantId(), id, request, ctx.userId()));
    }
}
