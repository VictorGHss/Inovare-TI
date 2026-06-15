package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente de infraestrutura que registra métricas customizadas de notificações e entregas do Blip.
 * Alimenta o Prometheus em tempo real para permitir a criação de alertas de SRE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlipNotificationMetrics {

    private final MeterRegistry registry;

    /**
     * Incrementa o contador blip_delivery_failures_total com base no código e descrição do erro.
     * Categoriza os códigos comuns do WhatsApp para simplificar exibições em painéis como o Grafana.
     *
     * @param errorCode Código de erro retornado pela plataforma Blip/Meta.
     * @param reason Descrição legível da falha recebida no webhook.
     */
    public void incrementFailureCount(Integer errorCode, String reason) {
        String codeStr = errorCode != null ? errorCode.toString() : "UNKNOWN";
        String category = resolveCategory(errorCode);

        log.debug("[MÉTRICAS] Incrementando métrica blip_delivery_failures_total para código: {}, categoria: {}", codeStr, category);

        Counter.builder("blip_delivery_failures_total")
                .description("Total de falhas críticas de entrega de mensagens enviadas via Blip")
                .tag("error_code", codeStr)
                .tag("reason_category", category)
                .register(registry)
                .increment();
    }

    /**
     * Categoriza os códigos comuns de erro de entrega do WhatsApp e LIME.
     *
     * @param errorCode Código numérico do erro.
     * @return String contendo a categoria legível da falha.
     */
    private String resolveCategory(Integer errorCode) {
        if (errorCode == null) {
            return "UNKNOWN";
        }
        return switch (errorCode) {
            case 131042 -> "LACK_OF_BALANCE";      // Falta de saldo na conta Meta
            case 131026 -> "INVALID_NUMBER";       // Número não registrado no WhatsApp / Bloqueado
            case 131047 -> "WINDOW_EXPIRED";       // Violação de janela de reengajamento de 24 horas
            case 131051, 132001 -> "TEMPLATE_REJECTED"; // Template de mensagem não aprovado ou inválido
            default -> {
                if (errorCode >= 10 && errorCode <= 99) {
                    yield "LIME_PROTOCOL_ERROR";
                }
                yield "PROVIDER_ERROR";
            }
        };
    }
}
