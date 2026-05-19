package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentVariableLog;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentVariableLogRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentVariableLogEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataAppointmentVariableLogRepository;
import lombok.RequiredArgsConstructor;

/**
 * Este é um Adaptador de Saída que implementa a Porta de Repositório do Domínio 
 * para fazer a ponte com o Spring Data JPA para os logs de variáveis de agendamento.
 */
@Component
@RequiredArgsConstructor
public class AppointmentVariableLogRepositoryAdapter implements AppointmentVariableLogRepositoryPort {

    private final SpringDataAppointmentVariableLogRepository springDataRepository;

    @Override
    public Optional<AppointmentVariableLog> findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(UUID sessionId, String dictionaryKey) {
        return springDataRepository.findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(sessionId, dictionaryKey)
                .map(AppointmentVariableLogEntity::toDomain);
    }

    @Override
    public AppointmentVariableLog save(AppointmentVariableLog log) {
        AppointmentVariableLogEntity entity = AppointmentVariableLogEntity.fromDomain(log);
        AppointmentVariableLogEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }
}
