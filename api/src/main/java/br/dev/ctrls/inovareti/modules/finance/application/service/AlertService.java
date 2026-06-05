package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.port.SystemAlertRepository;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Observed
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
        // Publica evento para listeners assíÆ’í‚Â­ncronos (ex.: notificaíÆ’í‚Â§íÆ’í‚Â£o via Discord)
        eventPublisher.publishEvent(alert);
    }

    /**
     * Registra um alerta permanente permitindo informar a severidade desejada.
     * Usado por outras rotinas (ex.: integraíÆ’í‚Â§íÆ’í‚Âµes) que queiram controlar o níÆ’í‚Â­vel
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
        // Publica evento para listeners assíÆ’í‚Â­ncronos (ex.: notificaíÆ’í‚Â§íÆ’í‚Â£o via Discord)
        eventPublisher.publishEvent(alert);
    }

    /**
     * Registra um alerta permanente com tipo e severidade explicitados.
     * Usado para criar alertas especíÆ’í‚Â­ficos (ex.: FINANCEIRO_RECEIPT_CRITICAL).
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

    // MíÆ’í‚Â©todo de envio direto ao Discord removido íÂ¢ââ€šÂ¬ââ‚¬Â o sistema agora publica eventos
    // `SystemAlert` e listeners assíÆ’í‚Â­ncronos (ex.: `AlertEventListener`) cuidam do
    // encaminhamento para canais externos (Discord). Isso evita que falhas no
    // envio impactem o fluxo principal.
}



