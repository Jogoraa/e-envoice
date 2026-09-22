package et.ut.einvoice.catalog.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.catalog.domain.Category;
import et.ut.einvoice.catalog.domain.Product;
import et.ut.einvoice.catalog.dto.CreateProductRequest;
import et.ut.einvoice.catalog.dto.ProductDto;
import et.ut.einvoice.catalog.repository.CategoryRepository;
import et.ut.einvoice.catalog.repository.ProductRepository;
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
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> searchProducts(UUID tenantId, UUID branchId, String query, String categoryCode, Boolean isActive, Pageable pageable) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String cleanCategory = (categoryCode != null && !categoryCode.isBlank()) ? categoryCode.trim().toUpperCase() : null;
        return productRepository.searchProducts(tenantId, branchId, cleanQuery, cleanCategory, isActive, pageable)
                .map(ProductDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts(UUID tenantId) {
        return productRepository.findByTenantIdOrderByItemCodeAsc(tenantId)
                .stream().map(ProductDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(UUID tenantId, UUID id) {
        return productRepository.findByTenantIdAndId(tenantId, id)
                .map(ProductDto::fromEntity)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found with ID: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<ProductDto> getProductByBarcode(UUID tenantId, String barcode) {
        return productRepository.findByTenantIdAndBarcode(tenantId, barcode)
                .map(ProductDto::fromEntity);
    }

    @Transactional
    public ProductDto registerProduct(UUID tenantId, CreateProductRequest request, String operatorId) {
        String itemCode = request.itemCode().trim().toUpperCase();
        if (productRepository.existsByTenantIdAndItemCode(tenantId, itemCode)) {
            throw new BusinessException("DUPLICATE_ITEM_CODE", "A product with item code '" + itemCode + "' already exists.", HttpStatus.CONFLICT);
        }

        UUID categoryId = request.categoryId();
        String categoryCode = request.categoryCode();
        if (categoryId != null) {
            Optional<Category> cat = categoryRepository.findByTenantIdAndId(tenantId, categoryId);
            if (cat.isPresent()) {
                categoryCode = cat.get().getCode();
            }
        } else if (categoryCode != null && !categoryCode.isBlank()) {
            Optional<Category> cat = categoryRepository.findByTenantIdAndCode(tenantId, categoryCode.trim().toUpperCase());
            if (cat.isPresent()) {
                categoryId = cat.get().getId();
                categoryCode = cat.get().getCode();
            }
        }

        Product product = new Product(
                UUID.randomUUID(),
                tenantId,
                request.branchId(),
                itemCode,
                request.sku().trim().toUpperCase(),
                request.barcode(),
                request.description().trim(),
                categoryId,
                categoryCode,
                request.unit(),
                request.unitPrice(),
                request.taxClassification(),
                request.trackStock() == null || request.trackStock(),
                request.stockQuantity()
        );

        Product saved = productRepository.save(product);
        log.info("Registered product '{}' ({}) for tenant {}", saved.getDescription(), saved.getItemCode(), tenantId);

        auditService.recordEvent(
                tenantId,
                operatorId != null ? operatorId : "TENANT_USER",
                "REGISTER_PRODUCT",
                "PRODUCT",
                saved.getId().toString(),
                String.format("{\"itemCode\":\"%s\",\"sku\":\"%s\",\"unitPrice\":%s}", saved.getItemCode(), saved.getSku(), saved.getUnitPrice())
        );

        return ProductDto.fromEntity(saved);
    }

    @Transactional
    public ProductDto updateProduct(UUID tenantId, UUID id, CreateProductRequest request, String operatorId) {
        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found with ID: " + id, HttpStatus.NOT_FOUND));

        product.setDescription(request.description().trim());
        product.setSku(request.sku().trim().toUpperCase());
        product.setBarcode(request.barcode());
        product.setUnit(request.unit());
        product.setUnitPrice(request.unitPrice());
        if (request.taxClassification() != null) {
            product.setTaxClassification(request.taxClassification().trim().toUpperCase());
        }
        if (request.trackStock() != null) {
            product.setTrackStock(request.trackStock());
        }
        if (request.stockQuantity() != null) {
            product.setStockQuantity(request.stockQuantity());
        }
        product.setUpdatedAt(Instant.now());

        Product saved = productRepository.save(product);
        return ProductDto.fromEntity(saved);
    }
}
