package br.dev.ctrls.inovareti.domain.appointment;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class WebhookIdempotencyService {

    private final StringRedisTemplate redis;

    @Value("${app.appointment.webhook-idempotency-hours}")
    private long webhookIdempotencyHours;

    public boolean registerIfFirstTime(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            log.error("messageId ausente ao registrar idempotência de webhook. Permitindo processamento (fail-open).");
            return true;
        }

        String idempotencyKey = "webhook:idempotency:{" + messageId.trim() + "}";

        try {
            Boolean inserted = redis.opsForValue().setIfAbsent(
                    idempotencyKey,
                    "1",
                    Duration.ofHours(webhookIdempotencyHours));

            return Boolean.TRUE.equals(inserted);
        } catch (RedisConnectionFailureException ex) {
            log.error(
                    "Redis indisponível ao registrar idempotência de webhook. key={}, ttlHours={}. Permitindo processamento (fail-open).",
                    idempotencyKey,
                    webhookIdempotencyHours,
                    ex);
            return true;
        } catch (RuntimeException ex) {
            log.error(
                    "Falha ao registrar idempotência de webhook no Redis. key={}, ttlHours={}. Permitindo processamento (fail-open).",
                    idempotencyKey,
                    webhookIdempotencyHours,
                    ex);
            return true;
        }
    }

    public boolean registerActionIfFirstTime(String appointmentId, String action) {
        if (appointmentId == null || appointmentId.isBlank() || action == null || action.isBlank()) {
            return true; // Fail open
        }

        String actionKey = "webhook:action:{" + appointmentId.trim() + "}:{" + action.trim() + "}";

        try {
            // Lock for 30 seconds to prevent double triggers and retries
            Boolean inserted = redis.opsForValue().setIfAbsent(
                    actionKey,
                    "1",
                    Duration.ofSeconds(30));

            return Boolean.TRUE.equals(inserted);
        } catch (Exception ex) {
            log.error("Falha ao registrar idempotência de ação no Redis. key={}", actionKey, ex);
            return true;
        }
    }
}
