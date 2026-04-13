package br.dev.ctrls.inovareti.domain.appointment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentSessionRepository extends JpaRepository<AppointmentSession, UUID> {

    Optional<AppointmentSession> findByFeegowAppointmentId(String feegowAppointmentId);

    List<AppointmentSession> findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus status, LocalDateTime threshold);
}
