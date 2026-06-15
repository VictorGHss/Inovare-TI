package br.dev.ctrls.inovareti.infrastructure.shared.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Decorator para tarefas assíncronas (TaskDecorator) que propaga o contexto do MDC (Mapped Diagnostic Context)
 * da thread principal (que recebeu a requisição HTTP) para as threads secundárias assíncronas do Spring.
 * Garante a rastreabilidade ponta a ponta (end-to-end) de logs por meio do Trace ID.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Copia o mapa de contexto do MDC da thread chamadora (principal)
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        
        return () -> {
            try {
                if (contextMap != null) {
                    // Define o contexto na thread assíncrona
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                // Saneamento de Threads: Limpa o contexto da thread no final da execução
                // para mitigar riscos de vazamento de memória (ThreadLocal leakage).
                MDC.clear();
            }
        };
    }
}
