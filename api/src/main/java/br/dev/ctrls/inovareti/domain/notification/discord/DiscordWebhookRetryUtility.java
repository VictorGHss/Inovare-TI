package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.port.SystemAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitário responsável por gerenciar a resiliência no envio de notificações para o Discord.
 *
 * Implementa retries com backoff exponencial e captura de rate limit dinâmico (HTTP 429)
 * em conformidade com as Virtual Threads do Java 21.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordWebhookRetryUtility {

    private final RestTemplate restTemplate;
    private final SystemAlertRepository systemAlertRepository;

    @Value("${discord.webhook.retry.max-attempts:3}")
    private int webhookMaxAttempts;

    @Value("${discord.webhook.retry.backoff-ms:500}")
    private long webhookRetryBackoffMs;

    /**
     * Envia o embed rico para a URL do webhook do Discord usando retries e resiliência.
     *
     * @param webhook URL do webhook do Discord.
     * @param embed Estrutura de dados do embed que compõe a mensagem.
     * @param contextType Tipo do contexto da mensagem (ex: chamado, alerta operacional).
     * @param contextId ID associado ao contexto para fins de log e alerta de erro.
     * @return true se o envio foi bem-sucedido, false caso contrário.
     */
    public boolean sendEmbedWithRetry(
            String webhook,
            Map<String, Object> embed,
            String contextType,
            String contextId) {
        if (!StringUtils.hasText(webhook)) {
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of("embeds", List.of(embed));
        int maxAttempts = Math.max(webhookMaxAttempts, 1);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.postForEntity(webhook, new HttpEntity<>(payload, headers), Void.class);
                log.info("Embed do Discord enviado com sucesso para {}={} na tentativa {}/{}.",
                        contextType,
                        contextId,
                        attempt,
                        maxAttempts);
                return true;
            } catch (HttpClientErrorException.TooManyRequests tmr) {
                // Tratamento dinâmico de HTTP 429 (Rate Limit) do Discord
                HttpHeaders responseHeaders = tmr.getResponseHeaders();
                long retryAfterMs = parseRetryAfter(responseHeaders);

                if (attempt < maxAttempts) {
                    log.warn("Rate limit atingido (429) no Discord para {}={} (tentativa {}/{}). "
                            + "Aguardando {} ms (Retry-After) antes da próxima tentativa.",
                            contextType,
                            contextId,
                            attempt,
                            maxAttempts,
                            retryAfterMs);
                    sleep(retryAfterMs);
                    // Decrementamos a tentativa para que a chamada do rate limit não consuma uma tentativa real
                    // permitindo que ela seja reenviada com sucesso.
                    attempt--;
                    continue;
                }

                log.error("Rate limit persistente no Discord para {}={} após {} tentativa(s): {}",
                        contextType,
                        contextId,
                        maxAttempts,
                        tmr.getMessage());
                registerOperationalSendFailure(
                        "Rate limit persistente (429)",
                        tmr.getMessage(),
                        webhook,
                        contextType,
                        contextId);
                return false;

            } catch (HttpClientErrorException.NotFound nf) {
                log.error("Webhook Discord inválido (404) para {}={}: {}", contextType, contextId, nf.getMessage());
                registerOperationalSendFailure(
                        "Webhook Discord inválido (404)",
                        nf.getMessage(),
                        webhook,
                        contextType,
                        contextId);
                return false;

            } catch (RestClientException ex) {
                long backoffMs = calculateExponentialBackoff(attempt);
                if (attempt < maxAttempts) {
                    log.warn("Falha de rede ao enviar embed no Discord para {}={} (tentativa {}/{}). Retentando em {} ms...",
                            contextType,
                            contextId,
                            attempt,
                            maxAttempts,
                            backoffMs);
                    sleep(backoffMs);
                    continue;
                }

                log.error("Falha ao enviar embed no Discord para {}={} após {} tentativa(s): {}",
                        contextType,
                        contextId,
                        maxAttempts,
                        ex.getMessage(),
                        ex);
                registerOperationalSendFailure(
                        "Falha ao enviar embed no Discord",
                        ex.getMessage(),
                        webhook,
                        contextType,
                        contextId);
                return false;

            } catch (Exception ex) {
                long backoffMs = calculateExponentialBackoff(attempt);
                if (attempt < maxAttempts) {
                    log.warn("Erro inesperado ao enviar embed no Discord para {}={} (tentativa {}/{}). Retentando em {} ms...",
                            contextType,
                            contextId,
                            attempt,
                            maxAttempts,
                            backoffMs,
                            ex);
                    sleep(backoffMs);
                    continue;
                }

                log.error("Erro inesperado ao enviar embed no Discord para {}={} após {} tentativa(s): {}",
                        contextType,
                        contextId,
                        maxAttempts,
                        ex.getMessage(),
                        ex);
                registerOperationalSendFailure(
                        "Erro inesperado ao enviar embed no Discord",
                        ex.getMessage(),
                        webhook,
                        contextType,
                        contextId);
                return false;
            }
        }

        return false;
    }

    private long parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return webhookRetryBackoffMs;
        }
        String retryAfterHeader = headers.getFirst("Retry-After");
        if (!StringUtils.hasText(retryAfterHeader)) {
            return webhookRetryBackoffMs;
        }
        try {
            // Discord retorna 'Retry-After' em segundos (pode ser ponto flutuante)
            double seconds = Double.parseDouble(retryAfterHeader.trim());
            return (long) (seconds * 1000.0);
        } catch (NumberFormatException nfe) {
            try {
                // Fallback para milissegundos inteiros se for um número longo simples
                return Long.parseLong(retryAfterHeader.trim());
            } catch (NumberFormatException e) {
                log.warn("Não foi possível decodificar o header Retry-After '{}': {}. Usando backoff padrão.",
                        retryAfterHeader, e.getMessage());
                return webhookRetryBackoffMs;
            }
        }
    }

    private long calculateExponentialBackoff(int attempt) {
        long factor = (long) Math.pow(2, attempt - 1);
        return Math.max(webhookRetryBackoffMs, 0L) * factor;
    }

    private void sleep(long durationMs) {
        if (durationMs <= 0) {
            return;
        }
        try {
            // Em Virtual Threads no Java 21, Thread.sleep cede a execução de forma não-bloqueante à Thread de Carrier
            Thread.sleep(durationMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Thread de retry do webhook do Discord foi interrompida.");
        }
    }

    private void registerOperationalSendFailure(
            String title,
            String details,
            String webhook,
            String contextType,
            String contextId) {
        String resolvedDetails = StringUtils.hasText(details) ? details : "Sem detalhes";
        try {
            systemAlertRepository.save(SystemAlert.builder()
                    .alertType("DISCORD_OPERATIONAL_ALERT")
                    .severity("ERROR")
                    .source("DiscordWebhookService")
                    .title(title)
                    .details(resolvedDetails)
                    .context(Map.of(
                            "webhook", webhook,
                            "contextType", contextType,
                            "contextId", contextId))
                    .build());
        } catch (Exception ex) {
            log.warn("Falha ao registrar SystemAlert após erro no webhook do Discord: {}", ex.getMessage(), ex);
        }
    }
}

