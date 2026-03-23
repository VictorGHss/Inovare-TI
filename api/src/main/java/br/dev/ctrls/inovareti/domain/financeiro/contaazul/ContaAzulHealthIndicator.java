package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContaAzulHealthIndicator implements HealthIndicator {

    private final ContaAzulOAuthTokenRepository tokenRepository;

    @Override
    public Health health() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(this::buildHealth)
                .orElse(Health.down().withDetail("reason", "no_token_found").build());
    }

    private Health buildHealth(ContaAzulOAuthToken token) {
        LocalDateTime expiresAt = token.getExpiresAt();
        if (expiresAt == null) {
            return Health.down()
                    .withDetail("reason", "expiresAt_missing")
                    .withDetail("token_preview", preview(token.getAccessToken()))
                    .build();
        }

        long minutesLeft = java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes();
        if (expiresAt.isBefore(LocalDateTime.now())) {
            return Health.down()
                    .withDetail("reason", "expired")
                    .withDetail("minutes_left", minutesLeft)
                    .withDetail("token_preview", preview(token.getAccessToken()))
                    .build();
        }

        return Health.up()
                .withDetail("minutes_left", minutesLeft)
                .withDetail("expires_at", expiresAt)
                .withDetail("token_preview", preview(token.getAccessToken()))
                .build();
    }

    private String preview(String t) {
        if (t == null) return "n/a";
        return t.length() <= 10 ? t : t.substring(0, 10) + "...";
    }
}
