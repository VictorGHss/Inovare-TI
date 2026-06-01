package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.application.service.AlertService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Listener responsÃƒÂ¡vel por reagir ÃƒÂ  criaÃƒÂ§ÃƒÂ£o de `SystemAlert` e encaminhar
 * notificaÃƒÂ§ÃƒÂµes para canais externos (ex.: Discord).
 *
 * ObservaÃƒÂ§ÃƒÂ£o tÃƒÂ©cnica:
 * - O uso de eventos Spring desacopla o envio de notificaÃƒÂ§ÃƒÂµes do fluxo
 *   principal de processamento financeiro. O `AlertService` publica o
 *   objeto `SystemAlert` como evento; este listener processa o evento
 *   assincronamente. Falhas no envio ao Discord nÃƒÂ£o irÃƒÂ£o bloquear o
 *   processamento das automaÃƒÂ§ÃƒÂµes de ContaAzul.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEventListener {

    private final DiscordWebhookService discordWebhookService;

    @Async
    @EventListener
    public void onSystemAlertCreated(SystemAlert alert) {
        if (alert == null) {
            return;
        }

        // Filtra apenas o tipo de alerta crÃƒÂ­tico financeiro desejado
        if (!"FINANCEIRO_RECEIPT_CRITICAL".equals(alert.getAlertType())) {
            return;
        }

        Map<String, Object> ctx = alert.getContext();
        String parcelaId = ctx != null && ctx.get("parcelaId") != null ? String.valueOf(ctx.get("parcelaId")) : "(desconhecido)";
        String doctorName = ctx != null && ctx.get("doctorName") != null ? String.valueOf(ctx.get("doctorName")) : null;

        String title = alert.getTitle() != null ? alert.getTitle() : "Alerta crÃƒÂ­tico financeiro";

        StringBuilder message = new StringBuilder();
        message.append("**").append(title).append("**\n");
        message.append("Parcela ID: ").append(parcelaId).append("\n");
        if (doctorName != null && !doctorName.isBlank()) {
            message.append("MÃƒÂ©dico: ").append(doctorName).append("\n");
        }
        message.append("\nPor favor, verifique o painel financeiro do Inovare TI e confirme se o recibo foi gerado/anexado.");

        try {
            discordWebhookService.sendOperationalAlert(title, message.toString());
        } catch (Exception ex) {
            log.error("Erro ao enviar notificaÃƒÂ§ÃƒÂ£o operacional no Discord para alerta {}: {}", alert.getId(), ex.getMessage(), ex);
        }
    }
}

