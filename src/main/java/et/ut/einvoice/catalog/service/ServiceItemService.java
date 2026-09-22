package et.ut.einvoice.catalog.service;

import et.ut.einvoice.audit.service.AuditService;
import et.ut.einvoice.catalog.domain.Category;
import et.ut.einvoice.catalog.domain.ServiceItem;
import et.ut.einvoice.catalog.dto.CreateServiceRequest;
import et.ut.einvoice.catalog.dto.ServiceItemDto;
import et.ut.einvoice.catalog.repository.CategoryRepository;
import et.ut.einvoice.catalog.repository.ServiceItemRepository;
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
public class ServiceItemService {

    private static final Logger log = LoggerFactory.getLogger(ServiceItemService.class);

    private final ServiceItemRepository serviceItemRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public ServiceItemService(ServiceItemRepository serviceItemRepository, CategoryRepository categoryRepository, AuditService auditService) {
        this.serviceItemRepository = serviceItemRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ServiceItemDto> searchServices(UUID tenantId, UUID branchId, String query, String categoryCode, Boolean isActive, Pageable pageable) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String cleanCategory = (categoryCode != null && !categoryCode.isBlank()) ? categoryCode.trim().toUpperCase() : null;
        return serviceItemRepository.searchServices(tenantId, branchId, cleanQuery, cleanCategory, isActive, pageable)
                .map(ServiceItemDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDto> getAllServices(UUID tenantId) {
        return serviceItemRepository.findByTenantIdOrderByServiceCodeAsc(tenantId)
                .stream().map(ServiceItemDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public ServiceItemDto getServiceById(UUID tenantId, UUID id) {
        return serviceItemRepository.findByTenantIdAndId(tenantId, id)
                .map(ServiceItemDto::fromEntity)
                .orElseThrow(() -> new BusinessException("SERVICE_NOT_FOUND", "Service not found with ID: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ServiceItemDto registerService(UUID tenantId, CreateServiceRequest request, String operatorId) {
        String serviceCode = request.serviceCode().trim().toUpperCase();
        if (serviceItemRepository.existsByTenantIdAndServiceCode(tenantId, serviceCode)) {
            throw new BusinessException("DUPLICATE_SERVICE_CODE", "A service with code '" + serviceCode + "' already exists.", HttpStatus.CONFLICT);
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

        ServiceItem serviceItem = new ServiceItem(
                UUID.randomUUID(),
                tenantId,
                request.branchId(),
                serviceCode,
                request.name().trim(),
                request.description(),
                categoryId,
                categoryCode,
                request.unit(),
                request.unitPrice(),
                request.taxClassification()
        );

        ServiceItem saved = serviceItemRepository.save(serviceItem);
        log.info("Registered service '{}' ({}) for tenant {}", saved.getName(), saved.getServiceCode(), tenantId);

        auditService.recordEvent(
                tenantId,
                operatorId != null ? operatorId : "TENANT_USER",
                "REGISTER_SERVICE",
                "SERVICE",
                saved.getId().toString(),
                String.format("{\"serviceCode\":\"%s\",\"name\":\"%s\",\"unitPrice\":%s}", saved.getServiceCode(), saved.getName(), saved.getUnitPrice())
        );

        return ServiceItemDto.fromEntity(saved);
    }

    @Transactional
    public ServiceItemDto updateService(UUID tenantId, UUID id, CreateServiceRequest request, String operatorId) {
        ServiceItem service = serviceItemRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException("SERVICE_NOT_FOUND", "Service not found with ID: " + id, HttpStatus.NOT_FOUND));

        service.setName(request.name().trim());
        service.setDescription(request.description());
        service.setUnit(request.unit());
        service.setUnitPrice(request.unitPrice());
        if (request.taxClassification() != null) {
            service.setTaxClassification(request.taxClassification().trim().toUpperCase());
        }
        service.setUpdatedAt(Instant.now());

        ServiceItem saved = serviceItemRepository.save(service);
        return ServiceItemDto.fromEntity(saved);
    }
}
