package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.DoctorConfigurationEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataDoctorConfigurationRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador JPA que implementa a porta DoctorConfigurationRepository.
 */
@Component
@RequiredArgsConstructor
public class DoctorConfigurationRepositoryAdapter implements DoctorConfigurationRepository {

    private final SpringDataDoctorConfigurationRepository springDataRepository;

    @Override
    public Optional<DoctorConfiguration> findById(Long id) {
        return springDataRepository.findById(id).map(entity -> entity.toDomain());
    }

    @Override
    public List<DoctorConfiguration> findAll() {
        return springDataRepository.findAll().stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public DoctorConfiguration save(DoctorConfiguration config) {
        DoctorConfigurationEntity entity = DoctorConfigurationEntity.fromDomain(config);
        DoctorConfigurationEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void delete(DoctorConfiguration config) {
        springDataRepository.delete(DoctorConfigurationEntity.fromDomain(config));
    }

    @Override
    public void deleteById(Long id) {
        springDataRepository.deleteById(id);
    }
}
