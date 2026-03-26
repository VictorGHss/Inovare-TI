package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final SystemAlertRepository systemAlertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void registerPermanentFailure(String parcelaId, String details, Map<String, Object> context) {
        SystemAlert alert = SystemAlert.builder()
                .alertType("FINANCIAL_RECEIPT_DISPATCH")
                .severity("CRITICAL")
                .source("EmailRetryScheduler")
                .title("Falha definitiva no envio de recibo financeiro")
                .details(details)
                .context(context)
                .build();

        systemAlertRepository.save(alert);
        // Publica evento para listeners assíncronos (ex.: notificação via Discord)
        eventPublisher.publishEvent(alert);
    }

    /**
     * Registra um alerta permanente permitindo informar a severidade desejada.
     * Usado por outras rotinas (ex.: integrações) que queiram controlar o nível
     * de gravidade do alerta no banco de dados.
     */
    public void registerPermanentFailureWithSeverity(String parcelaId, String details, Map<String, Object> context, String severity) {
        SystemAlert alert = SystemAlert.builder()
                .alertType("FINANCIAL_RECEIPT_DISPATCH")
                .severity(severity != null ? severity : "CRITICAL")
                .source("ContaAzulAutomationService")
                .title("Falha definitiva no envio de recibo financeiro")
                .details(details)
                .context(context != null ? context : Map.of())
                .build();

        systemAlertRepository.save(alert);
        // Publica evento para listeners assíncronos (ex.: notificação via Discord)
        eventPublisher.publishEvent(alert);
    }

    /**
     * Registra um alerta permanente com tipo e severidade explicitados.
     * Usado para criar alertas específicos (ex.: FINANCEIRO_RECEIPT_CRITICAL).
     */
    public void registerPermanentFailureWithTypeAndSeverity(String parcelaId, String details, Map<String, Object> context, String alertType, String severity) {
        SystemAlert alert = SystemAlert.builder()
                .alertType(alertType != null ? alertType : "FINANCIAL_RECEIPT_DISPATCH")
                .severity(severity != null ? severity : "CRITICAL")
                .source("ContaAzulAutomationService")
                .title("Falha definitiva no envio de recibo financeiro")
                .details(details)
                .context(context != null ? context : Map.of())
                .build();

        systemAlertRepository.save(alert);
        eventPublisher.publishEvent(alert);
    }

    // Método de envio direto ao Discord removido — o sistema agora publica eventos
    // `SystemAlert` e listeners assíncronos (ex.: `AlertEventListener`) cuidam do
    // encaminhamento para canais externos (Discord). Isso evita que falhas no
    // envio impactem o fluxo principal.
}
