package br.dev.ctrls.inovareti.domain.report;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {
    List<ReportSchedule> findByIsActiveTrue();
}
