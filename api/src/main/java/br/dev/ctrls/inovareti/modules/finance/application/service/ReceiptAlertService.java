package br.dev.ctrls.inovareti.modules.finance.application.service;


import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÆ’Ã‚Â§o responsÃƒÆ’Ã‚Â¡vel por registrar alertas crÃƒÆ’Ã‚Â­ticos do fluxo de recibos.
 *
 * ObservaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o:
 * o registro do SystemAlert tambÃƒÆ’Ã‚Â©m publica evento assÃƒÆ’Ã‚Â­ncrono,
 * que ÃƒÆ’Ã‚Â© encaminhado ao Discord pelo listener financeiro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
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

        log.error("Alerta crÃƒÆ’Ã‚Â­tico registrado para baixa {} apÃƒÆ’Ã‚Â³s {} tentativas.", baixaId, attempts);
    }
}


