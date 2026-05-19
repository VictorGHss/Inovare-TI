package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentVariableLog;

public interface AppointmentVariableLogRepositoryPort {

    Optional<AppointmentVariableLog> findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(UUID sessionId, String dictionaryKey);

    AppointmentVariableLog save(AppointmentVariableLog log);
}
