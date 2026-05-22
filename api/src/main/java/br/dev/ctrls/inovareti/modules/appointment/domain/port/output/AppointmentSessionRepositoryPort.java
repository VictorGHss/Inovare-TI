package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;

public interface AppointmentSessionRepositoryPort {

    Optional<AppointmentSession> findById(UUID id);

    Optional<AppointmentSession> findByIdLocked(UUID id);

    Optional<AppointmentSession> findByFeegowAppointmentId(String feegowAppointmentId);

    List<AppointmentSession> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    List<AppointmentSession> findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    List<AppointmentSession> findActiveByPhoneNumber(String phone);

    List<AppointmentSession> findPendingNotifications();

    AppointmentSession save(AppointmentSession session);
}
