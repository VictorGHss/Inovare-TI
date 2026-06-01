package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulTokenService;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulClient;
import br.dev.ctrls.inovareti.modules.finance.application.service.ReceiptEmailService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.NoReceiptAvailableException;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.FinancialResponseMapper;

import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de infraestrutura (Outbound Adapter) responsÃƒÂ¡vel pela lÃƒÂ³gica de download fÃƒÂ­sico
 * dos PDFs dos recibos e resoluÃƒÂ§ÃƒÂ£o de anexos via APIs do Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulReceiptStorageAdapter {

    private static final int SETTLEMENT_DETAILS_MAX_ATTEMPTS = 2;
    private static final long SETTLEMENT_DETAILS_RETRY_WAIT_NANOS = 15_000_000_000L;

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final FinancialResponseMapper financialResponseMapper;
    private final ReceiptEmailService receiptEmailService;

    /**
     * Resolve a URL do recibo a partir dos anexos da baixa e baixa o PDF autenticado.
     */
    public byte[] downloadReceiptPdf(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalArgumentException("baixaId nÃƒÂ£o pode ser nulo/vazio para baixar recibo.");
        }

        String normalizedBaixaId = baixaId.trim();
        String receiptUrl = resolveReceiptUrlFromSettlementWithRetry(normalizedBaixaId)
                .orElseThrow(() -> new NoReceiptAvailableException(
                        "Nenhum anexo de recibo encontrado para baixa " + normalizedBaixaId));

        String accessToken = contaAzulTokenService.getValidAccessToken();
        return receiptEmailService.downloadReceiptBinary(receiptUrl, accessToken);
    }

    /**
     * Busca detalhes da baixa para extrair URL do recibo.
     *
     * Regra: quando o array de anexos vier vazio, aguarda 15 segundos e tenta
     * mais uma vez (mÃƒÂ¡ximo 2 tentativas).
     */
    private Optional<String> resolveReceiptUrlFromSettlementWithRetry(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            log.warn("resolveReceiptUrlFromSettlementWithRetry chamado com baixaId vazio.");
            return Optional.empty();
        }

        String normalizedBaixaId = baixaId.trim();

        for (int attempt = 1; attempt <= SETTLEMENT_DETAILS_MAX_ATTEMPTS; attempt++) {
            Optional<JsonNode> settlementNodeOpt = contaAzulClient.getSettlementDetails(normalizedBaixaId);
            if (settlementNodeOpt.isEmpty()) {
                return Optional.empty();
            }

            JsonNode settlementNode = settlementNodeOpt.get();
            Optional<String> receiptUrlOpt = financialResponseMapper.extractReceiptUrl(settlementNode);
            if (receiptUrlOpt.isPresent()) {
                return receiptUrlOpt;
            }

            boolean attachmentsEmpty = financialResponseMapper.isSettlementAttachmentsEmpty(settlementNode);
            if (!attachmentsEmpty || attempt >= SETTLEMENT_DETAILS_MAX_ATTEMPTS) {
                return Optional.empty();
            }

            log.info(
                    "Anexos ainda vazios para baixa {} na tentativa {}/{}. Aguardando 15 segundos para nova consulta.",
                    normalizedBaixaId,
                    attempt,
                    SETTLEMENT_DETAILS_MAX_ATTEMPTS);
            waitBeforeSettlementRetry();
            if (Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private void waitBeforeSettlementRetry() {
        LockSupport.parkNanos(SETTLEMENT_DETAILS_RETRY_WAIT_NANOS);
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrompida durante espera de retry para anexos da baixa.");
        }
    }
}

