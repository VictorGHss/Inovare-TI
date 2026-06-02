package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;

/**
 * Interface estratÃ©gia (Strategy Pattern) para o processamento de aÃ§Ãµes especÃ­ficas
 * disparadas pelos webhooks do Blip.
 */
public interface BlipWebhookActionHandler {

    /**
     * Determina se a implementaÃ§Ã£o suporta o tipo de aÃ§Ã£o informado.
     * 
     * @param actionType tipo da aÃ§Ã£o ("confirm" ou "alter")
     * @return {@code true} se a aÃ§Ã£o for suportada, {@code false} caso contrÃ¡rio
     */
    boolean supports(String actionType);

    /**
     * Executa a lÃ³gica de prÃ©-persistÃªncia externa do webhook (ex.: chamadas de integraÃ§Ã£o).
     * 
     * @param session dados atuais da sessÃ£o de agendamento carregados do banco
     * @param action a aÃ§Ã£o completa recebida no webhook
     */
    void prePersistence(AppointmentSession session, String action);

    /**
     * Executa modificaÃ§Ãµes especÃ­ficas de estado na entidade da sessÃ£o de agendamento.
     * Este mÃ©todo deve rodar dentro da transaÃ§Ã£o de gravaÃ§Ã£o microscÃ³pica.
     * 
     * @param session a entidade de sessÃ£o de agendamento carregada transacionalmente
     * @param action a aÃ§Ã£o completa recebida no webhook
     */
    void applySessionState(AppointmentSession session, String action);
}


