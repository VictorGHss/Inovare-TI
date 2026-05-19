package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentConfigEntity;

public interface SpringDataAppointmentConfigRepository extends JpaRepository<AppointmentConfigEntity, UUID> {

    Optional<AppointmentConfigEntity> findByCategory(AppointmentCategory category);
}