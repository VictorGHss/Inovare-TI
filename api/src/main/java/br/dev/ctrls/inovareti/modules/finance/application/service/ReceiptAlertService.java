package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;


import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃ§o responsÃ¡vel por registrar alertas crÃ­ticos do fluxo de recibos.
 *
 * ObservaÃ§Ã£o:
 * o registro do SystemAlert tambÃ©m publica evento assÃ­ncrono,
 * que Ã© encaminhado ao Discord pelo listener financeiro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class ReceiptAlertService {

    private final AlertService alertService;

    public void notifyPermanentReceiptFailure(
            String baixaId,
            String saleId,
            String doctorName,
            int attempts,
            String details) {
        Map<String, Object> context = new HashMap<>();
        context.put("parcelaId", baixaId);

        if (StringUtils.hasText(saleId)) {
            context.put("saleId", saleId);
        }

        if (StringUtils.hasText(doctorName)) {
            context.put("doctorName", doctorName);
        }

        context.put("attempts", attempts);

        alertService.registerPermanentFailureWithTypeAndSeverity(
                baixaId,
                details,
                context,
                "FINANCEIRO_RECEIPT_CRITICAL",
                "HIGH");

        log.error("Alerta crÃ­tico registrado para baixa {} apÃ³s {} tentativas.", baixaId, attempts);
    }
}




