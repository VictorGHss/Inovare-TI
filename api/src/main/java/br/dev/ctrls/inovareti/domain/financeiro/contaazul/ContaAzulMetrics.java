package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Exposição de métricas Prometheus relacionadas ao token ContaAzul.
 *
 * Registra dois `Gauge`s:
 * - `contaazul_token_expires_at`: epoch seconds da expiração do token
 * - `contaazul_last_refresh_timestamp`: epoch seconds do último refresh bem-sucedido
 *
 * Atualiza as métricas periodicamente via `@Scheduled` e pode ser desabilitado
 * com a propriedade `app.contaazul.metrics.enabled=false`.
 */
@Component
@ConditionalOnProperty(name = "app.contaazul.metrics.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class ContaAzulMetrics {

    /** Repositório de tokens ContaAzul utilizado para ler o token mais recentemente salvo. */
    private final ContaAzulOAuthTokenRepository tokenRepository;

    /** Registry do Micrometer para registro das métricas. */
    private final MeterRegistry registry;

    /** Valor do Gauge que armazena epoch seconds da expiração do token. */
    private final AtomicLong expiresAtGauge = new AtomicLong(0L);

    /** Valor do Gauge que armazena segundos restantes até a expiração do token. */
    private final AtomicLong expiresInSecondsGauge = new AtomicLong(0L);

    /** Valor do Gauge que armazena epoch seconds do último refresh bem-sucedido. */
    private final AtomicLong lastRefreshGauge = new AtomicLong(0L);

    /** Contador de throttles ocorridos no endpoint force-refresh. */
    private io.micrometer.core.instrument.Counter forceRefreshThrottledCounter;

    /**
     * Registra os Gauges no `MeterRegistry`.
     *
     * Observação: as descrições dos próprios gauges são mantidas inalteradas
     * para preservar metadados já expostos ao Prometheus.
     */
    @PostConstruct
    public void registerGauges() {
        Gauge.builder("contaazul_token_expires_at", expiresAtGauge, AtomicLong::get)
            .description("Unix epoch seconds when the ContaAzul token expires")
            .register(registry);

        Gauge.builder("contaazul_token_expires_seconds", expiresInSecondsGauge, AtomicLong::get)
            .description("Seconds until ContaAzul token expiration (0 if expired)")
            .register(registry);

        Gauge.builder("contaazul_last_refresh_timestamp", lastRefreshGauge, AtomicLong::get)
            .description("Unix epoch seconds of last successful token refresh")
            .register(registry);

        // contador para eventos de throttling no endpoint administrativo
        this.forceRefreshThrottledCounter = io.micrometer.core.instrument.Counter
            .builder("contaazul_force_refresh_throttled_total")
            .description("Total de requisições force-refresh que foram throttled")
            .register(registry);
    }

    /**
     * Atualiza periodicamente os valores dos Gauges a partir do token persistido.
     * Executado em intervalo fixo para refletir alterações de expiração e refresh.
     */
    @Scheduled(fixedDelayString = "60000")
    public void updateMetrics() {
        tokenRepository.findTopByOrderByUpdatedAtDesc().ifPresent(token -> {
            if (token.getExpiresAt() != null) {
                expiresAtGauge.set(token.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
                long now = java.time.Instant.now().getEpochSecond();
                long expiresAt = token.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                long diff = Math.max(0L, expiresAt - now);
                expiresInSecondsGauge.set(diff);
            }
            if (token.getRefreshedAt() != null) {
                lastRefreshGauge.set(token.getRefreshedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
            }
        });
    }

    /** Incrementa o contador de throttles. Chamado pelo controller quando uma requisição é bloqueada. */
    public void incrementForceRefreshThrottled() {
        if (this.forceRefreshThrottledCounter != null) {
            this.forceRefreshThrottledCounter.increment();
        }
    }

}
