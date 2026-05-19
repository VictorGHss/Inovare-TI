package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentConfigEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataAppointmentConfigRepository;
import lombok.RequiredArgsConstructor;

/**
 * Este é um Adaptador de Saída que implementa a Porta de Repositório do Domínio 
 * para fazer a ponte com o Spring Data JPA para as configurações de agendamento.
 */
@Component
@RequiredArgsConstructor
public class AppointmentConfigRepositoryAdapter implements AppointmentConfigRepositoryPort {

    private final SpringDataAppointmentConfigRepository springDataRepository;

    @Override
    public Optional<AppointmentConfig> findByCategory(AppointmentCategory category) {
        return springDataRepository.findByCategory(category).map(AppointmentConfigEntity::toDomain);
    }

    @Override
    public Optional<AppointmentConfig> findById(UUID id) {
        return springDataRepository.findById(id).map(AppointmentConfigEntity::toDomain);
    }

    @Override
    public List<AppointmentConfig> findAll() {
        return springDataRepository.findAll().stream()
                .map(AppointmentConfigEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public AppointmentConfig save(AppointmentConfig config) {
        AppointmentConfigEntity entity = AppointmentConfigEntity.fromDomain(config);
        AppointmentConfigEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }
}
