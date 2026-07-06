package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;

/**
 * Interface estratégia (Strategy Pattern) para o processamento de ações específicas
 * disparadas pelos webhooks do Blip.
 */
public interface BlipWebhookActionHandler {

    /**
     * Determina se a implementação suporta o tipo de ação informado.
     * 
     * @param actionType tipo da ação ("confirm" ou "alter")
     * @return {@code true} se a ação for suportada, {@code false} caso contrário
     */
    boolean supports(String actionType);

    /**
     * Executa a lógica de pré-persistência externa do webhook (ex.: chamadas de integração).
     * 
     * @param session dados atuais da sessão de agendamento carregados do banco
     * @param action a ação completa recebida no webhook
     * @param fromIdentity a identidade que disparou a ação no webhook (pode ser telefone master ou túnel)
     */
    void prePersistence(AppointmentSession session, String action, String fromIdentity);

    /**
     * Executa modificações específicas de estado na entidade da sessão de agendamento.
     * Este método deve rodar dentro da transação de gravação microscópica.
     * 
     * @param session a entidade de sessão de agendamento carregada transacionalmente
     * @param action a ação completa recebida no webhook
     * @param fromIdentity a identidade que disparou a ação no webhook (pode ser telefone master ou túnel)
     */
    void applySessionState(AppointmentSession session, String action, String fromIdentity);
}


