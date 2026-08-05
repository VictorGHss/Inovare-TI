package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de solicitação de alteração de consulta ("alter").
 */
@Slf4j
@Component
@Observed
public class AlterBlipWebhookActionHandler implements BlipWebhookActionHandler {

    @Override
    public boolean supports(String actionType) {
        return "alter".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session, String action, String fromIdentity) {
        log.info("[ALTERAR] Iniciando processamento de solicitação de alteração.");
    }

    @Override
    public void applySessionState(AppointmentSession session, String action, String fromIdentity) {
        session.setStatus(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.ALTERATION_REQUESTED);
        session.setClosedAt(java.time.LocalDateTime.now());
        log.info("[ALTERAR] Sessão ID {} atualizada para ALTERATION_REQUESTED. Lembretes automáticos suspensos.", session.getId());
    }
}


