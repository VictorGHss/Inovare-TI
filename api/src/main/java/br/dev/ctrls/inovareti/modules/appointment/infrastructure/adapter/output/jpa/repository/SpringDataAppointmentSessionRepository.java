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

    List<AppointmentSessionEntity> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

    List<AppointmentSessionEntity> findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);

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
}