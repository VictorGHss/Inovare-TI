package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConfirmationStateMachineService {

    public AppointmentSession markNudge1Sent(AppointmentSession session) {
        session.setStatus(AppointmentSessionStatus.NUDGE_1_SENT);
        session.setLastInteractionAt(LocalDateTime.now());
        session.setLastNotificationSentAt(LocalDateTime.now());
        log.info("Transicao de estado aplicada. appointmentId={} novoEstado={}",
                session.getFeegowAppointmentId(), session.getStatus());
        return session;
    }

    public AppointmentSession markNudgeFinalSent(AppointmentSession session) {
        session.setStatus(AppointmentSessionStatus.NUDGE_FINAL_SENT);
        session.setLastInteractionAt(LocalDateTime.now());
        session.setLastNotificationSentAt(LocalDateTime.now());
        log.info("Transicao de estado aplicada. appointmentId={} novoEstado={}",
                session.getFeegowAppointmentId(), session.getStatus());
        return session;
    }

    public AppointmentSession markConfirmed(AppointmentSession session) {
        session.setStatus(AppointmentSessionStatus.CONFIRMED);
        session.setLastInteractionAt(LocalDateTime.now());
        session.setClosedAt(LocalDateTime.now());
        log.info("Transicao de estado aplicada. appointmentId={} novoEstado={}",
                session.getFeegowAppointmentId(), session.getStatus());
        return session;
    }

    public AppointmentSession markCanceledByNoResponse(AppointmentSession session) {
        session.setStatus(AppointmentSessionStatus.CANCELED_NO_RESPONSE);
        session.setLastInteractionAt(LocalDateTime.now());
        session.setClosedAt(LocalDateTime.now());
        log.info("Transicao de estado aplicada. appointmentId={} novoEstado={}",
                session.getFeegowAppointmentId(), session.getStatus());
        return session;
    }
}
