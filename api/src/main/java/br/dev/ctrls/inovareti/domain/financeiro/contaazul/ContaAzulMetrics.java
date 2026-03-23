package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContaAzulMetrics {

    private final ContaAzulOAuthTokenRepository tokenRepository;
    private final MeterRegistry registry;

    private final AtomicLong expiresAtGauge = new AtomicLong(0L);
    private final AtomicLong lastRefreshGauge = new AtomicLong(0L);

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("contaazul_token_expires_at", expiresAtGauge, AtomicLong::get)
                .description("Unix epoch seconds when the ContaAzul token expires")
                .register(registry);

        Gauge.builder("contaazul_last_refresh_timestamp", lastRefreshGauge, AtomicLong::get)
                .description("Unix epoch seconds of last successful token refresh")
                .register(registry);
    }

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
