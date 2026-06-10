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

    Optional<AppointmentSession> findByFeegowAppointmentIdAndPhoneNumber(String feegowAppointmentId, String phoneNumber);

    Optional<AppointmentSession> findByIdAndPhoneNumber(UUID id, String phoneNumber);

    List<AppointmentSession> findByFeegowAppointmentIdIn(java.util.Collection<String> feegowAppointmentIds);

    // Método para buscar sessões associadas ao currentGroupId diretamente
    List<AppointmentSession> findByCurrentGroupId(UUID currentGroupId);

    List<AppointmentSession> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    List<AppointmentSession> findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    List<AppointmentSession> findActiveByPhoneNumber(String phone);

    List<AppointmentSession> findActiveByBlipGuid(String blipGuid);

    List<AppointmentSession> findActiveByBsuid(String bsuid);

    List<AppointmentSession> findPendingNotifications();

    AppointmentSession save(AppointmentSession session);

    long deleteByStatusInAndCreatedAtBefore(java.util.Collection<AppointmentSessionStatus> statuses, LocalDateTime threshold);
}
