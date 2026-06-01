package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.application.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÂ§o responsÃƒÂ¡vel por registrar alertas crÃƒÂ­ticos do fluxo de recibos.
 *
 * ObservaÃƒÂ§ÃƒÂ£o:
 * o registro do SystemAlert tambÃƒÂ©m publica evento assÃƒÂ­ncrono,
 * que ÃƒÂ© encaminhado ao Discord pelo listener financeiro.
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

        log.error("Alerta crÃƒÂ­tico registrado para baixa {} apÃƒÂ³s {} tentativas.", baixaId, attempts);
    }
}


