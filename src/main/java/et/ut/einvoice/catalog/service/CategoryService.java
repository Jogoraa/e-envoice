package et.ut.einvoice.catalog.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.catalog.domain.Category;
import et.ut.einvoice.catalog.dto.CategoryDto;
import et.ut.einvoice.catalog.dto.CreateCategoryRequest;
import et.ut.einvoice.catalog.repository.CategoryRepository;
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
import java.util.UUID;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<CategoryDto> searchCategories(UUID tenantId, String query, String categoryType, String status, Pageable pageable) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String cleanType = (categoryType != null && !categoryType.isBlank() && !"ALL".equalsIgnoreCase(categoryType)) ? categoryType.trim().toUpperCase() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        return categoryRepository.searchCategories(tenantId, cleanQuery, cleanType, cleanStatus, pageable)
                .map(CategoryDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllActiveCategories(UUID tenantId, String categoryType) {
        List<Category> list = categoryRepository.findByTenantIdAndStatusOrderByCodeAsc(tenantId, "ACTIVE");
        if (categoryType != null && !categoryType.isBlank() && !"ALL".equalsIgnoreCase(categoryType)) {
            String target = categoryType.trim().toUpperCase();
            return list.stream()
                    .filter(c -> c.getCategoryType().equalsIgnoreCase(target) || c.getCategoryType().equalsIgnoreCase("ALL"))
                    .map(CategoryDto::fromEntity)
                    .toList();
        }
        return list.stream().map(CategoryDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(UUID tenantId, UUID id) {
        return categoryRepository.findByTenantIdAndId(tenantId, id)
                .map(CategoryDto::fromEntity)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found with ID: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public CategoryDto createCategory(UUID tenantId, CreateCategoryRequest request, String operatorId) {
        String code = request.code().trim().toUpperCase();
        if (categoryRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new BusinessException("DUPLICATE_CATEGORY_CODE", "A category with code '" + code + "' already exists for this tenant.", HttpStatus.CONFLICT);
        }

        Category category = new Category(
                UUID.randomUUID(),
                tenantId,
                code,
                request.name().trim(),
                request.categoryType() != null ? request.categoryType().trim().toUpperCase() : "PRODUCT",
                request.description(),
                request.parentId()
        );

        Category saved = categoryRepository.save(category);
        log.info("Created category '{}' ({}) for tenant {}", saved.getName(), saved.getCode(), tenantId);

        auditService.recordEvent(
                tenantId,
                operatorId != null ? operatorId : "TENANT_USER",
                "CREATE_CATEGORY",
                "CATEGORY",
                saved.getId().toString(),
                String.format("{\"code\":\"%s\",\"name\":\"%s\",\"type\":\"%s\"}", saved.getCode(), saved.getName(), saved.getCategoryType())
        );

        return CategoryDto.fromEntity(saved);
    }

    @Transactional
    public CategoryDto updateCategory(UUID tenantId, UUID id, CreateCategoryRequest request, String operatorId) {
        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found with ID: " + id, HttpStatus.NOT_FOUND));

        String newCode = request.code().trim().toUpperCase();
        if (!category.getCode().equalsIgnoreCase(newCode) && categoryRepository.existsByTenantIdAndCode(tenantId, newCode)) {
            throw new BusinessException("DUPLICATE_CATEGORY_CODE", "A category with code '" + newCode + "' already exists.", HttpStatus.CONFLICT);
        }

        category.setCode(newCode);
        category.setName(request.name().trim());
        if (request.categoryType() != null) {
            category.setCategoryType(request.categoryType().trim().toUpperCase());
        }
        category.setDescription(request.description());
        category.setParentId(request.parentId());
        category.setUpdatedAt(Instant.now());

        Category saved = categoryRepository.save(category);
        log.info("Updated category '{}' ({}) for tenant {}", saved.getName(), saved.getCode(), tenantId);

        auditService.recordEvent(
                tenantId,
                operatorId != null ? operatorId : "TENANT_USER",
                "UPDATE_CATEGORY",
                "CATEGORY",
                saved.getId().toString(),
                String.format("{\"code\":\"%s\",\"name\":\"%s\"}", saved.getCode(), saved.getName())
        );

        return CategoryDto.fromEntity(saved);
    }

    @Transactional
    public CategoryDto toggleCategoryStatus(UUID tenantId, UUID id, String operatorId) {
        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found with ID: " + id, HttpStatus.NOT_FOUND));

        String newStatus = category.isActive() ? "INACTIVE" : "ACTIVE";
        category.setStatus(newStatus);
        category.setUpdatedAt(Instant.now());
        Category saved = categoryRepository.save(category);

        auditService.recordEvent(
                tenantId,
                operatorId != null ? operatorId : "TENANT_USER",
                "TOGGLE_CATEGORY_STATUS",
                "CATEGORY",
                saved.getId().toString(),
                "{\"status\":\"" + newStatus + "\"}"
        );

        return CategoryDto.fromEntity(saved);
    }
}
