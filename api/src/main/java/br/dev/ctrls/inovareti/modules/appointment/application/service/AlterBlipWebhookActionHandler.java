package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import lombok.extern.slf4j.Slf4j;

/**
 * EstratÃ©gia de processamento especÃ­fica para a aÃ§Ã£o de solicitaÃ§Ã£o de alteraÃ§Ã£o de consulta ("alter").
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
    public void prePersistence(AppointmentSession session, String action) {
        log.info("[ALTERAR] Iniciando processamento de solicitaÃ§Ã£o de alteraÃ§Ã£o.");
    }

    @Override
    public void applySessionState(AppointmentSession session, String action) {
        // Nenhuma alteraÃ§Ã£o de estado no banco de dados necessÃ¡ria alÃ©m da atualizaÃ§Ã£o comum do telefone.
        log.info("[MENSAGERIA] AÃ§Ã£o de {} processada com sucesso no banco e na Feegow. NavegaÃ§Ã£o entregue ao Builder nativo.", "alteraÃ§Ã£o");
    }
}


