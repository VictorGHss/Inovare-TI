package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import br.dev.ctrls.inovareti.modules.finance.domain.port.ContaAzulOAuthTokenRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;

class ContaAzulMetricsTest {

    @Test
    void gaugesRegisterAndUpdateFromRepository() {
        var repo = Mockito.mock(ContaAzulOAuthTokenRepository.class);
        var registry = new SimpleMeterRegistry();

        ContaAzulOAuthToken token = new ContaAzulOAuthToken();
        token.setId(UUID.randomUUID());
        LocalDateTime now = LocalDateTime.now();
        token.setExpiresAt(now.plusMinutes(30));
        token.setRefreshedAt(now.minusMinutes(5));

        when(repo.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(token));

        var metrics = new ContaAzulMetrics(repo, registry);
        metrics.init();

        // invoke update directly (scheduled in production)
        metrics.updateMetrics();

        long expectedExpires = token.getExpiresAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        long expectedRefreshed = token.getRefreshedAt().atZone(ZoneId.systemDefault()).toEpochSecond();

        double expiresVal = registry.get("contaazul_token_expires_at").gauge().value();
        double refreshedVal = registry.get("contaazul_last_refresh_timestamp").gauge().value();

        assertEquals(expectedExpires, (long) expiresVal);
        assertEquals(expectedRefreshed, (long) refreshedVal);
    }
}
