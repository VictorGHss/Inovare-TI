package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentDoctorMappingEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataAppointmentDoctorMappingRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de Saída que implementa a Porta de Repositório do Dominio 
 * para fazer a ponte com o Spring Data JPA para os mapeamentos de medicos de agendamento.
 */
@Component
@RequiredArgsConstructor
public class AppointmentDoctorMappingRepositoryAdapter implements AppointmentDoctorMappingRepositoryPort {

    private final SpringDataAppointmentDoctorMappingRepository springDataRepository;

    @Override
    public Optional<AppointmentDoctorMapping> findByProfissionalId(String profissionalId) {
        return springDataRepository.findByProfissionalId(profissionalId).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentDoctorMapping> findByProfissionalIdLocked(String profissionalId) {
        return springDataRepository.findByProfissionalIdLocked(profissionalId).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentDoctorMapping> findById(UUID id) {
        return springDataRepository.findById(id).map(entity -> entity.toDomain());
    }

    @Override
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }

    @Override
    public List<AppointmentDoctorMapping> findAll() {
        return springDataRepository.findAll().stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public AppointmentDoctorMapping save(AppointmentDoctorMapping mapping) {
        AppointmentDoctorMappingEntity entity = AppointmentDoctorMappingEntity.fromDomain(mapping);
        AppointmentDoctorMappingEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void delete(AppointmentDoctorMapping mapping) {
        springDataRepository.delete(AppointmentDoctorMappingEntity.fromDomain(mapping));
    }

    @Override
    public void deleteById(UUID id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public void deleteAll(List<AppointmentDoctorMapping> mappings) {
        List<AppointmentDoctorMappingEntity> entities = mappings.stream()
                .map(AppointmentDoctorMappingEntity::fromDomain)
                .collect(Collectors.toList());
        springDataRepository.deleteAll(entities);
    }
}