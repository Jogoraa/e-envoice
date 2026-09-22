package et.ut.einvoice.catalog.controller;

import et.ut.einvoice.catalog.dto.CategoryDto;
import et.ut.einvoice.catalog.dto.CreateCategoryRequest;
import et.ut.einvoice.catalog.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Category Master Management", description = "Tenant-scoped Category Master lifecycle, hierarchical grouping, and product/service association")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Search categories with pagination, query, and type filtering")
    public ResponseEntity<Page<CategoryDto>> searchCategories(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "type", required = false) String categoryType,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(categoryService.searchCategories(tenantId, query, categoryType, status, pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all active categories for tenant")
    public ResponseEntity<List<CategoryDto>> getAllActiveCategories(
            @RequestParam(name = "type", required = false) String categoryType
    ) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(categoryService.getAllActiveCategories(tenantId, categoryType));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredContext().tenantId();
        return ResponseEntity.ok(categoryService.getCategoryById(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('CASHIER') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Create a new tenant category record")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        var ctx = TenantContextHolder.getRequiredContext();
        CategoryDto created = categoryService.createCategory(ctx.tenantId(), request, ctx.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Update an existing category record")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        var ctx = TenantContextHolder.getRequiredContext();
        return ResponseEntity.ok(categoryService.updateCategory(ctx.tenantId(), id, request, ctx.userId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SCOPE_catalog:write') or hasRole('TENANT_ADMIN') or hasRole('DELEGATED_OPERATOR')")
    @Operation(summary = "Toggle active/inactive status of a category")
    public ResponseEntity<CategoryDto> toggleCategoryStatus(@PathVariable("id") UUID id) {
        var ctx = TenantContextHolder.getRequiredContext();
        return ResponseEntity.ok(categoryService.toggleCategoryStatus(ctx.tenantId(), id, ctx.userId()));
    }
}
