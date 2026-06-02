package br.dev.ctrls.inovareti.modules.report.domain.port.output;

import br.dev.ctrls.inovareti.modules.report.domain.model.ReportSchedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para persistência de agendamentos de relatórios.
 * Define o contrato puro Java, isolado de detalhes de framework.
 */
public interface ReportScheduleRepositoryPort {
    List<ReportSchedule> findAll();
    Optional<ReportSchedule> findById(UUID id);
    List<ReportSchedule> findByIsActiveTrue();
    ReportSchedule save(ReportSchedule schedule);
    void deleteById(UUID id);
}
