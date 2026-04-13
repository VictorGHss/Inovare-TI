package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentConfigRepository extends JpaRepository<AppointmentConfig, UUID> {

    Optional<AppointmentConfig> findByCategory(AppointmentCategory category);
}
