package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.DoctorConfigurationEntity;

/**
 * Interface Spring Data JPA para acesso à tabela doctor_configurations.
 */
public interface SpringDataDoctorConfigurationRepository extends JpaRepository<DoctorConfigurationEntity, Long> {
}
