package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final SystemAlertRepository systemAlertRepository;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${discord.webhook.url}")
    private String discordWebhookUrl;

    public void registerPermanentFailure(String parcelaId, String details, Map<String, Object> context) {
        SystemAlert alert = SystemAlert.builder()
                .alertType("FINANCIAL_RECEIPT_DISPATCH")
                .severity("CRITICAL")
                .source("EmailRetryScheduler")
                .title("Falha definitiva no envio de recibo financeiro")
                .details(details)
                .context(context)
                .build();

        systemAlertRepository.save(alert);
        // Publica evento para listeners assíncronos (ex.: notificação via Discord)
        eventPublisher.publishEvent(alert);
    }

    /**
     * Registra um alerta permanente permitindo informar a severidade desejada.
     * Usado por outras rotinas (ex.: integrações) que queiram controlar o nível
     * de gravidade do alerta no banco de dados.
     */
    public void registerPermanentFailureWithSeverity(String parcelaId, String details, Map<String, Object> context, String severity) {
        SystemAlert alert = SystemAlert.builder()
                .alertType("FINANCIAL_RECEIPT_DISPATCH")
                .severity(severity != null ? severity : "CRITICAL")
                .source("ContaAzulAutomationService")
                .title("Falha definitiva no envio de recibo financeiro")
                .details(details)
                .context(context != null ? context : Map.of())
                .build();

        systemAlertRepository.save(alert);
        // Publica evento para listeners assíncronos (ex.: notificação via Discord)
        eventPublisher.publishEvent(alert);
    }

    /**
     * Registra um alerta permanente com tipo e severidade explicitados.
     * Usado para criar alertas específicos (ex.: FINANCEIRO_RECEIPT_CRITICAL).
     */
    public void registerPermanentFailureWithTypeAndSeverity(String parcelaId, String details, Map<String, Object> context, String alertType, String severity) {
        SystemAlert alert = SystemAlert.builder()
                .alertType(alertType != null ? alertType : "FINANCIAL_RECEIPT_DISPATCH")
                .severity(severity != null ? severity : "CRITICAL")
                .source("ContaAzulAutomationService")
                .title("Falha definitiva no envio de recibo financeiro")
                .details(details)
                .context(context != null ? context : Map.of())
                .build();

        systemAlertRepository.save(alert);
        eventPublisher.publishEvent(alert);
    }

    private void sendDiscordAlert(String parcelaId, String details) {
        if (!StringUtils.hasText(discordWebhookUrl)) {
            log.warn("Discord webhook not configured. Alert for parcela {} recorded only in database.", parcelaId);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String message = "🚨 **Financeiro**: falha definitiva no envio do recibo da parcela `"
                + parcelaId
                + "`. Detalhes: "
                + details;

        try {
            restTemplate.postForEntity(discordWebhookUrl, new HttpEntity<>(Map.of("content", message), headers), Void.class);
        } catch (RestClientException ex) {
            log.error("Falha ao enviar alerta financeiro no Discord para parcela {}", parcelaId, ex);
        }
    }
}
