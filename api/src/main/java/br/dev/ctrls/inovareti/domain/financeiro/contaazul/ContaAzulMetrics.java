package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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
     * <p>
     * A anotação {@code @PostConstruct} garante que este método seja executado após o construtor
     * e a injeção de todas as dependências pelo Spring. Esta abordagem é preferível a chamar
     * lógica de inicialização no construtor, pois evita problemas com chamadas a métodos
     * que podem ser sobrescritos (warning "Overridable method call in constructor") e garante
     * que o componente esteja totalmente configurado.
     */
    @PostConstruct
    private void init() {
        registerGauges();
    }

    private void registerGauges() {
        log.info("Registrando gauges da Conta Azul");
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
