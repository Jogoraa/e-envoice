package et.ut.einvoice.metering.service;

import et.ut.einvoice.metering.domain.UsageRecord;
import et.ut.einvoice.metering.repository.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UsageMeteringService {

    private static final Logger log = LoggerFactory.getLogger(UsageMeteringService.class);

    private final UsageRecordRepository repository;

    public UsageMeteringService(UsageRecordRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional
    public void recordUsage(UUID tenantId, String metricType, BigDecimal quantity, String resourceId) {
        UsageRecord record = new UsageRecord(
                UUID.randomUUID(),
                tenantId,
                metricType,
                quantity != null ? quantity : BigDecimal.ONE,
                resourceId
        );
        repository.save(record);
        log.debug("Recorded usage for tenant {}: {} = {}", tenantId, metricType, quantity);
    }
}
