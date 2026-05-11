package br.dev.ctrls.inovareti.domain.appointment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentSessionRepository extends JpaRepository<AppointmentSession, UUID> {

    Optional<AppointmentSession> findByFeegowAppointmentId(String feegowAppointmentId);

    List<AppointmentSession> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    List<AppointmentSession> findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM AppointmentSession a WHERE a.id = :id")
    Optional<AppointmentSession> findByIdLocked(@org.springframework.data.repository.query.Param("id") UUID id);

    /**
     * Busca a sessão mais recente e ativa para um número de telefone.
     * Usada quando o usuário envia texto livre (ex: 'Solicitar Alteração') sem payload de botão.
     */
    @Query("SELECT a FROM AppointmentSession a WHERE a.phoneNumber = :phone " +
           "AND a.status NOT IN ('CONFIRMED', 'CANCELLED', 'EXPIRED') " +
           "ORDER BY a.lastInteractionAt DESC")
    List<AppointmentSession> findActiveByPhoneNumber(@Param("phone") String phone);
}
