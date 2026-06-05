package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por gerenciar a idempotência dos webhooks do Blip,
 * incluindo registro de mensagem processada, locks atômicos e spin-wait não bloqueante.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class BlipIdempotencyService {

    private final Optional<WebhookIdempotencyService> webhookIdempotencyService;
    private final Optional<NoopWebhookIdempotencyService> noopWebhookIdempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Verifica se o messageId do Blip já foi processado anteriormente.
     * 
     * @param messageId identificador único da mensagem recebida do webhook
     * @return {@code true} se for o primeiro processamento (fresh), {@code false} se for duplicado
     */
    public boolean registerIfFirstTime(String messageId) {
        return webhookIdempotencyService
                .map(service -> service.registerIfFirstTime(messageId))
                .orElseGet(() -> noopWebhookIdempotencyService
                        .map(service -> service.registerIfFirstTime(messageId))
                        .orElse(true));
    }

    /**
     * Tenta adquirir uma trava atômica no Redis para processamento concorrente do agendamento.
     * 
     * @param appointmentId ID do agendamento Feegow
     * @return {@code true} se o bloqueio foi adquirido, {@code false} caso contrário
     */
    public boolean tryAcquireLock(String appointmentId) {
        return webhookIdempotencyService
                .map(service -> service.tryAcquireLock(appointmentId))
                .orElseGet(() -> noopWebhookIdempotencyService
                        .map(service -> service.tryAcquireLock(appointmentId))
                        .orElse(true));
    }

    /**
     * Salva em cache o resultado final estruturado do processamento de um agendamento.
     * 
     * @param appointmentId ID do agendamento Feegow
     * @param result resultado a ser serializado e cacheado
     */
    public void saveCachedResult(String appointmentId, HandleBlipWebhookUseCase.WebhookResult result) {
        if (result == null) return;
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            webhookIdempotencyService.ifPresent(service -> service.saveCachedResult(appointmentId, jsonResult));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("[IDEMPOTENCY] Erro ao serializar resultado final para cache. appointmentId={}, erro={}", appointmentId, e.getMessage(), e);
        }
    }

    /**
     * Efetua spin-wait concorrente aguardando que o resultado de outra thread ativa seja persistido em cache.
     * 
     * @param appointmentId ID do agendamento Feegow
     * @param actionType tipo da ação executada (confirm ou alter)
     * @return resultado em cache resolvido, ou {@code null} se estourar o timeout
     */
    public HandleBlipWebhookUseCase.WebhookResult getCachedResultOrSpinWait(String appointmentId, String actionType) {
        log.info("[IDEMPOTENCY] Aguardando processamento da thread principal para o agendamento {}", appointmentId);
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            awaitIdempotencyDelayNonBlocking(500L); // Delay não bloqueante seguro para Virtual Threads

            String cachedJson = getCachedResult(appointmentId);
            if (cachedJson != null) {
                try {
                    log.info("[IDEMPOTENCY] Resultado lido do cache para agendamento {}", appointmentId);
                    HandleBlipWebhookUseCase.WebhookResult cachedResult = objectMapper.readValue(cachedJson, HandleBlipWebhookUseCase.WebhookResult.class);
                    return new HandleBlipWebhookUseCase.WebhookResult(
                        cachedResult.queue(),
                        cachedResult.patientName(),
                        cachedResult.patientCPF(),
                        cachedResult.patientBirthdate(),
                        actionType,
                        cachedResult.doctorName()
                    );
                } catch (JsonProcessingException | IllegalArgumentException e) {
                    log.error("[IDEMPOTENCY] Erro ao ler cache JSON para agendamento {}", appointmentId, e);
                }
            }
        }
        log.warn("[IDEMPOTENCY] Tempo esgotado aguardando resultado para agendamento {}. Retornando null.", appointmentId);
        return null;
    }

    private String getCachedResult(String appointmentId) {
        return webhookIdempotencyService
                .map(service -> service.getCachedResult(appointmentId))
                .orElseGet(() -> noopWebhookIdempotencyService
                        .map(service -> service.getCachedResult(appointmentId))
                        .orElse(null));
    }

    private void awaitIdempotencyDelayNonBlocking(long delayMs) {
        LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delayMs));
    }
}


