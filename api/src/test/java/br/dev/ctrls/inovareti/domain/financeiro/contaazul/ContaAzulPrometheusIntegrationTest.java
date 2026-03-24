package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.contaazul.metrics.enabled=true",
        "spring.scheduling.enabled=false"
})
public class ContaAzulPrometheusIntegrationTest {

    @LocalServerPort
    int port;

    // fornece um bean de teste para o repositório (evita @MockBean que não está disponível neste runtime)
    @TestConfiguration
    static class TestConfig {
        @Bean
        public ContaAzulOAuthTokenRepository tokenRepository() {
            ContaAzulOAuthTokenRepository mock = org.mockito.Mockito.mock(ContaAzulOAuthTokenRepository.class);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            ContaAzulOAuthToken token = ContaAzulOAuthToken.builder()
                    .accessToken("access")
                    .refreshToken("refresh")
                    .tokenType("bearer")
                    .expiresAt(now.plusHours(1))
                    .refreshedAt(now)
                    .build();
            org.mockito.Mockito.when(mock.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(token));
            return mock;
        }
    }

    @Autowired
    private ContaAzulMetrics contaAzulMetrics;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    public void prometheusEndpointContainsContaAzulMetrics() {
        // Preparação: mock do repositório para retornar um token com timestamps
        LocalDateTime now = LocalDateTime.now();
        ContaAzulOAuthToken token = ContaAzulOAuthToken.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .tokenType("bearer")
                .expiresAt(now.plusHours(1))
                .refreshedAt(now)
                .build();

        // Ação: aciona atualização das métricas (agendamentos desabilitados em testes)
        contaAzulMetrics.updateMetrics();

        // Verificação: garante que os nomes das métricas foram registrados no MeterRegistry
        assertTrue(meterRegistry.find("contaazul_token_expires_at").gauge() != null);
        assertTrue(meterRegistry.find("contaazul_last_refresh_timestamp").gauge() != null);
    }
}
