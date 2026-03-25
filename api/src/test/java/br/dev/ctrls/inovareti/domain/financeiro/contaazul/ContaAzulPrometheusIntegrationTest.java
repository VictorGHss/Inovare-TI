package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.contaazul.metrics.enabled=true",
        "spring.scheduling.enabled=false"
})
@Import(ContaAzulPrometheusIntegrationTest.TestConfig.class)
public class ContaAzulPrometheusIntegrationTest {


    // fornece um bean de teste para o repositório (evita @MockBean que não está disponível neste runtime)
    @TestConfiguration
    static class TestConfig {
        @Bean
        @org.springframework.context.annotation.Primary
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
        // Preparação: o bean de teste já fornece um token via TestConfig

        // Ação: aciona atualização das métricas (agendamentos desabilitados em testes)
        contaAzulMetrics.updateMetrics();

        // Verificação: garante que os nomes das métricas foram registrados no MeterRegistry
        assertTrue(meterRegistry.find("contaazul_token_expires_at").gauge() != null);
        assertTrue(meterRegistry.find("contaazul_last_refresh_timestamp").gauge() != null);
    }
}
