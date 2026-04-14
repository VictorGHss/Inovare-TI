package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentVariableLogRepository extends JpaRepository<AppointmentVariableLog, UUID> {

	Optional<AppointmentVariableLog> findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(
			UUID sessionId,
			String dictionaryKey);
}
