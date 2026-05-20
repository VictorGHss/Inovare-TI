package br.dev.ctrls.inovareti.modules.appointment.application.service;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de solicitação de alteração de consulta ("alter").
 */
@Slf4j
@Component
public class AlterBlipWebhookActionHandler implements BlipWebhookActionHandler {

    @Override
    public boolean supports(String actionType) {
        return "alter".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session) {
        log.info("[ALTERAR] Paciente solicita alteração. Redirecionando para fila humana.");
    }

    @Override
    public void applySessionState(AppointmentSession session) {
        // Nenhuma alteração de estado no banco de dados necessária além da atualização comum do telefone.
    }
}
