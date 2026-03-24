package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ContaAzulMetrics {
    private final Counter forceRefreshThrottledCounter;

    public ContaAzulMetrics(MeterRegistry registry) {
        this.forceRefreshThrottledCounter = Counter.builder("contaazul.force.refresh.throttled")
                .description("Total de requisições de force-refresh bloqueadas por rate limit")
                .register(registry);
    }

    public void incrementForceRefreshThrottled() {
        this.forceRefreshThrottledCounter.increment();
    }
}
