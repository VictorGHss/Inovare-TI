package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Map;

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
        sendDiscordAlert(parcelaId, details);
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
            log.error("Failed to send Discord financial alert for parcela {}", parcelaId, ex);
        }
    }
}
