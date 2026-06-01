package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentSessionEntity;

import jakarta.persistence.LockModeType;

public interface SpringDataAppointmentSessionRepository extends JpaRepository<AppointmentSessionEntity, UUID> {

    Optional<AppointmentSessionEntity> findByFeegowAppointmentId(String feegowAppointmentId);

    List<AppointmentSessionEntity> findByFeegowAppointmentIdIn(java.util.Collection<String> feegowAppointmentIds);

    // Método para buscar sessões associadas ao currentGroupId diretamente na tabela de agendamentos
    List<AppointmentSessionEntity> findByCurrentGroupId(UUID currentGroupId);

    List<AppointmentSessionEntity> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppointmentSessionEntity> findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    @Query("SELECT a FROM AppointmentSessionEntity a WHERE a.status = 'PENDING' AND a.lastNotificationSentAt IS NULL")
    List<AppointmentSessionEntity> findPendingNotifications();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AppointmentSessionEntity a WHERE a.id = :id")
    Optional<AppointmentSessionEntity> findByIdLocked(@Param("id") UUID id);

    /**
     * Busca a sessao mais recente e ativa para um numero de telefone.
     * Usada quando o usuario envia texto livre (ex: 'Solicitar Alteracao') sem payload de botao.
     */
    @Query("SELECT a FROM AppointmentSessionEntity a WHERE a.phoneNumber = :phone " +
           "AND a.status NOT IN ('CONFIRMED', 'CANCELLED', 'EXPIRED') " +
           "ORDER BY a.lastInteractionAt DESC")
    List<AppointmentSessionEntity> findActiveByPhoneNumber(@Param("phone") String phone);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM AppointmentSessionEntity a WHERE a.status IN :statuses AND a.createdAt < :threshold")
    long deleteByStatusInAndCreatedAtBefore(@Param("statuses") java.util.Collection<br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus> statuses, @Param("threshold") LocalDateTime threshold);
}