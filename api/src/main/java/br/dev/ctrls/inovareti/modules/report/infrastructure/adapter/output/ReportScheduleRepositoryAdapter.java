package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.report.domain.model.ReportSchedule;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportScheduleRepositoryPort;
import br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output.jpa.repository.SpringDataReportScheduleRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de infraestrutura que implementa a porta de saída de persistência
 * encapsulando o Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class ReportScheduleRepositoryAdapter implements ReportScheduleRepositoryPort {

    private final SpringDataReportScheduleRepository repository;

    @Override
    public List<ReportSchedule> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<ReportSchedule> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<ReportSchedule> findByIsActiveTrue() {
        return repository.findByIsActiveTrue();
    }

    @Override
    public ReportSchedule save(ReportSchedule schedule) {
        return repository.save(schedule);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
