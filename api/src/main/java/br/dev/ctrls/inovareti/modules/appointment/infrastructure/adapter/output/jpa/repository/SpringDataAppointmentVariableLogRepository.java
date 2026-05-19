package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentVariableLogEntity;

public interface SpringDataAppointmentVariableLogRepository extends JpaRepository<AppointmentVariableLogEntity, UUID> {

    List<AppointmentVariableLogEntity> findByCategory(AppointmentCategory category);

    Optional<AppointmentVariableLogEntity> findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(UUID sessionId, String dictionaryKey);
}