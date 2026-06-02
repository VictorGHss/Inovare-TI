package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ContaAzulOAuthTokenRepository;

import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

@Component
public class ContaAzulMetrics {

    private static final Logger log = LoggerFactory.getLogger(ContaAzulMetrics.class);

    private final ContaAzulOAuthTokenRepository repository;
    private final MeterRegistry registry;

    private final Counter forceRefreshThrottledCounter;
    private final AtomicLong expiresAt = new AtomicLong(0);
    private final AtomicLong refreshedAt = new AtomicLong(0);

    private volatile ContaAzulOAuthToken lastToken;

    public ContaAzulMetrics(ContaAzulOAuthTokenRepository repository, MeterRegistry registry) {
        this.repository = repository;
        this.registry = registry;
        this.forceRefreshThrottledCounter = Counter.builder("contaazul.force.refresh.throttled")
                .description("Total de requisições de force-refresh bloqueadas por rate limit")
                .register(registry);
    }

    /**
     * Inicializa o registro dos gauges após a injeção de dependências.
     *
     * Observação: este método é invocado pelo container do Spring através de
     * {@code @PostConstruct}. O método precisa ser público para que analisadores
     * e frameworks reconheçam sua finalidade sem dependências de suposições
     * sobre modificadores de acesso. Comentários e explicações estão em Português.
     */
    @PostConstruct
    public void init() {
        registerGauges();
    }

    private void registerGauges() {
        // Registramos os gauges e emitimos um log amigável indicando onde o Prometheus
        // pode coletar as métricas. Este log serve como "simulação" de verificação
        // quando a aplicação sobe em ambientes onde não é possível executar uma
        // requisição HTTP de verificação neste momento.
        log.info("Registrando gauges da Conta Azul");
        log.info("Endpoint de métricas Prometheus: /api/actuator/prometheus (verifique com curl se a API estiver rodando)");
        registry.gauge("contaazul_token_expires_at", expiresAt);
        registry.gauge("contaazul_last_refresh_timestamp", refreshedAt);
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 30000)
    public synchronized void updateMetrics() {
        findAndCacheLatestToken();
        if (this.lastToken != null) {
            this.expiresAt.set(this.lastToken.getExpiresAt().atZone(ZoneId.systemDefault()).toEpochSecond());
            this.refreshedAt.set(this.lastToken.getRefreshedAt().atZone(ZoneId.systemDefault()).toEpochSecond());
        } else {
            this.expiresAt.set(0);
            this.refreshedAt.set(0);
        }
    }

    private void findAndCacheLatestToken() {
        try {
            Optional<ContaAzulOAuthToken> tokenOpt = repository.findTopByOrderByUpdatedAtDesc();
            if (tokenOpt.isPresent()) {
                this.lastToken = tokenOpt.get();
            } else {
                log.debug("Nenhum token da Conta Azul encontrado para as métricas.");
                this.lastToken = null;
            }
        } catch (Exception e) {
            log.error("Falha ao buscar token da Conta Azul para métricas", e);
            this.lastToken = null;
        }
    }

    public void incrementForceRefreshThrottled() {
        this.forceRefreshThrottledCounter.increment();
    }
}

