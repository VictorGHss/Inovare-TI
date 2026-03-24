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

    /** Valor do Gauge que armazena epoch seconds do último refresh bem-sucedido. */
    private final AtomicLong lastRefreshGauge = new AtomicLong(0L);

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

        Gauge.builder("contaazul_last_refresh_timestamp", lastRefreshGauge, AtomicLong::get)
            .description("Unix epoch seconds of last successful token refresh")
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
            }
            if (token.getRefreshedAt() != null) {
                lastRefreshGauge.set(token.getRefreshedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
            }
        });
    }

}
