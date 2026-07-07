package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentSessionEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataAppointmentSessionRepository;
import lombok.RequiredArgsConstructor;

/**
 * Este é um Adaptador de Saída que implementa a Porta de Repositório do Domínio 
 * para fazer a ponte com o Spring Data JPA para as sessões de agendamento.
 */
@Component
@RequiredArgsConstructor
public class AppointmentSessionRepositoryAdapter implements AppointmentSessionRepositoryPort {

    private final SpringDataAppointmentSessionRepository springDataRepository;

    @Override
    public Optional<AppointmentSession> findById(UUID id) {
        return springDataRepository.findById(id).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentSession> findByIdLocked(UUID id) {
        return springDataRepository.findByIdLocked(id).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentSession> findByFeegowAppointmentId(String feegowAppointmentId) {
        return springDataRepository.findByFeegowAppointmentId(feegowAppointmentId).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentSession> findByFeegowAppointmentIdAndPhoneNumber(String feegowAppointmentId, String phoneNumber) {
        return springDataRepository.findByFeegowAppointmentIdAndPhoneNumber(feegowAppointmentId, phoneNumber).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentSession> findByIdAndPhoneNumber(UUID id, String phoneNumber) {
        return springDataRepository.findByIdAndPhoneNumber(id, phoneNumber).map(entity -> entity.toDomain());
    }

    @Override
    public List<AppointmentSession> findByFeegowAppointmentIdIn(java.util.Collection<String> feegowAppointmentIds) {
        return springDataRepository.findByFeegowAppointmentIdIn(feegowAppointmentIds).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findByCurrentGroupId(UUID currentGroupId) {
        return springDataRepository.findByCurrentGroupId(currentGroupId).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold) {
        return springDataRepository.findByStatusAndLastInteractionAtBefore(status, threshold).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus status, LocalDateTime threshold) {
        return springDataRepository.findByStatusAndLastNotificationSentAtBefore(status, threshold).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findActiveByPhoneNumber(String phone) {
        return springDataRepository.findActiveByPhoneNumber(phone).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findActiveByBlipGuid(String blipGuid) {
        if (blipGuid == null || blipGuid.isBlank()) {
            return List.of();
        }
        return springDataRepository.findActiveByBlipGuid(blipGuid.trim()).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findActiveByBsuid(String bsuid) {
        if (bsuid == null || bsuid.isBlank()) {
            return List.of();
        }
        return springDataRepository.findActiveByBsuid(bsuid.trim()).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentSession> findPendingNotifications() {
        return springDataRepository.findPendingNotifications().stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public AppointmentSession save(AppointmentSession session) {
        AppointmentSessionEntity entity = AppointmentSessionEntity.fromDomain(session);
        AppointmentSessionEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public long deleteByStatusInAndCreatedAtBefore(java.util.Collection<AppointmentSessionStatus> statuses, LocalDateTime threshold) {
        return springDataRepository.deleteByStatusInAndCreatedAtBefore(statuses, threshold);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        return springDataRepository.existsByPhoneNumber(phoneNumber.trim());
    }
}
