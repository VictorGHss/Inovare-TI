package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.report.domain.model.ReportSchedule;

/**
 * Interface física do Spring Data JPA para operações com a tabela report_schedules.
 */
@Repository
public interface SpringDataReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {
    List<ReportSchedule> findByIsActiveTrue();
}
