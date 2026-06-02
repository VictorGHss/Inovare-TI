package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.DiscordWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Listener responsÃ¡vel por reagir Ã  criaÃ§Ã£o de `SystemAlert` e encaminhar
 * notificaÃ§Ãµes para canais externos (ex.: Discord).
 *
 * ObservaÃ§Ã£o tÃ©cnica:
 * - O uso de eventos Spring desacopla o envio de notificaÃ§Ãµes do fluxo
 *   principal de processamento financeiro. O `AlertService` publica o
 *   objeto `SystemAlert` como evento; este listener processa o evento
 *   assincronamente. Falhas no envio ao Discord nÃ£o irÃ£o bloquear o
 *   processamento das automaÃ§Ãµes de ContaAzul.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Observed
public class AlertEventListener {

    private final DiscordWebhookService discordWebhookService;

    @Async
    @EventListener
    public void onSystemAlertCreated(SystemAlert alert) {
        if (alert == null) {
            return;
        }

        // Filtra apenas o tipo de alerta crÃ­tico financeiro desejado
        if (!"FINANCEIRO_RECEIPT_CRITICAL".equals(alert.getAlertType())) {
            return;
        }

        Map<String, Object> ctx = alert.getContext();
        String parcelaId = ctx != null && ctx.get("parcelaId") != null ? String.valueOf(ctx.get("parcelaId")) : "(desconhecido)";
        String doctorName = ctx != null && ctx.get("doctorName") != null ? String.valueOf(ctx.get("doctorName")) : null;

        String title = alert.getTitle() != null ? alert.getTitle() : "Alerta crÃ­tico financeiro";

        StringBuilder message = new StringBuilder();
        message.append("**").append(title).append("**\n");
        message.append("Parcela ID: ").append(parcelaId).append("\n");
        if (doctorName != null && !doctorName.isBlank()) {
            message.append("MÃ©dico: ").append(doctorName).append("\n");
        }
        message.append("\nPor favor, verifique o painel financeiro do Inovare TI e confirme se o recibo foi gerado/anexado.");

        try {
            discordWebhookService.sendOperationalAlert(title, message.toString());
        } catch (Exception ex) {
            log.error("Erro ao enviar notificaÃ§Ã£o operacional no Discord para alerta {}: {}", alert.getId(), ex.getMessage(), ex);
        }
    }
}



