package et.ut.einvoice.reports.repository;

import et.ut.einvoice.reports.domain.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, String> {

    List<ReportDefinition> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<ReportDefinition> findAllByOrderByDisplayOrderAsc();
}
