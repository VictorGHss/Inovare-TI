package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialLinkRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceipt;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedReceiptRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceiptStatus;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialLink;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.modules.notification.application.service.FinanceEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Observed
public class ReceiptDispatcher {

    private final FinancialLinkRepository financialLinkRepository;
    private final ProcessedReceiptRepository processedReceiptRepository;
    private final CustomerEmailSyncService emailSyncService;
    private final FinanceEmailService financeEmailService;

    public void dispatchReceipt(ContaAzulPaymentParcel parcela, byte[] pdfBytes, String receiptHash) {
        dispatchReceiptInternal(parcela, pdfBytes, receiptHash, true);
    }

    public void dispatchReceiptWithoutPdf(ContaAzulPaymentParcel parcela, String receiptHash) {
        dispatchReceiptInternal(parcela, null, receiptHash, false);
    }

    private void dispatchReceiptInternal(
            ContaAzulPaymentParcel parcela,
            byte[] pdfBytes,
            String receiptHash,
            boolean includePdfAttachment) {
        FinancialLink financialLink = resolveFinancialLink(parcela);

        ProcessedReceipt receipt = processedReceiptRepository.findByParcelaId(parcela.parcelaId())
                .orElseGet(ProcessedReceipt::new);

        receipt.setFinancialLink(financialLink);
        receipt.setParcelaId(parcela.parcelaId());
        receipt.setReceiptHash(receiptHash);
        receipt.setOriginalRecipientEmail(resolveRecipientEmail(parcela, financialLink));

        try {
            routeReceipt(parcela, financialLink, receipt, pdfBytes, includePdfAttachment);
            receipt.setStatus(ProcessedReceiptStatus.SENT);
            receipt.setRetryCount(0);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setPayload(buildPayload(receipt, null, financialLink.getCanal().name(), false, parcela.saleNumber()));
            processedReceiptRepository.save(receipt);
        } catch (RuntimeException ex) {
            // Em caso de falha de rede ou timeout com a API da Conta Azul, define como retentável no ecrã de controlo local
            int nextRetryCount = receipt.getRetryCount() + 1;
            receipt.setStatus(ProcessedReceiptStatus.FAILED_RETRYABLE);
            receipt.setRetryCount(nextRetryCount);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setPayload(buildPayload(receipt, ex.getMessage(), financialLink.getCanal().name(), true, parcela.saleNumber()));
            processedReceiptRepository.save(receipt);
            throw ex;
        }
    }

    /**
     * Regista uma nova tentativa falhada, mantendo o registo no estado de retentativa assíncrona.
     */
    public ProcessedReceipt markRetryFailure(ProcessedReceipt receipt, String errorMessage) {
        int nextRetryCount = receipt.getRetryCount() + 1;
        receipt.setStatus(ProcessedReceiptStatus.FAILED_RETRYABLE);
        receipt.setRetryCount(nextRetryCount);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, errorMessage, receipt.getFinancialLink().getCanal().name(), true, null));
        return processedReceiptRepository.save(receipt);
    }

    /**
     * Regista uma falha definitiva (Dead-Letter Queue lógica) para auditoria pelo administrador.
     */
    public ProcessedReceipt markPermanentFailure(ProcessedReceipt receipt, String errorMessage) {
        receipt.setStatus(ProcessedReceiptStatus.FAILED_PERMANENT);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, errorMessage, receipt.getFinancialLink().getCanal().name(), false, null));
        return processedReceiptRepository.save(receipt);
    }

    public ProcessedReceipt saveHistoricalReceipt(ProcessedReceipt receipt, String channel) {
        receipt.setStatus(ProcessedReceiptStatus.HISTORICO);
        receipt.setRetryCount(0);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, null, channel, false, null));
        return processedReceiptRepository.save(receipt);
    }

    private void routeReceipt(
            ContaAzulPaymentParcel parcela,
            FinancialLink financialLink,
            ProcessedReceipt receipt,
            byte[] pdfBytes,
            boolean includePdfAttachment) {
        // Garante linguagem consistente no e-mail mesmo quando a API níÆ’í†â€™íâ€ší‚Â£o retorna níÆ’í†â€™íâ€ší‚Âºmero comercial.
        String saleNumber = StringUtils.hasText(parcela.saleNumber()) ? parcela.saleNumber().trim() : "N/D";

        if (financialLink.getCanal() == FinancialLink.Canal.DISCORD) {
            throw new IllegalStateException(
                    "Canal DISCORD níÆ’í†â€™íâ€ší‚Â£o possui destinatíÆ’í†â€™íâ€ší‚Â¡rio vinculado no financial_link para customerId="
                            + parcela.customerId());
        }

        if (includePdfAttachment && pdfBytes != null && pdfBytes.length > 0) {
            financeEmailService.sendReceiptEmailWithPdf(
                parcela.medicoNome(),
                receipt.getOriginalRecipientEmail(),
                "OlíÆ’í†â€™íâ€ší‚Â¡ " + parcela.medicoNome() + ", seu recibo financeiro referente íÆ’í†â€™íâ€ší‚Â  Venda " + saleNumber + " estíÆ’í†â€™íâ€ší‚Â¡ em anexo.",
                pdfBytes,
                "recibo-venda-" + saleNumber + ".pdf",
                saleNumber);
            return;
        }

        financeEmailService.sendReceiptEmail(
            parcela.medicoNome(),
            receipt.getOriginalRecipientEmail(),
            "OlíÆ’í†â€™íâ€ší‚Â¡ " + parcela.medicoNome() + ", registramos o recebimento financeiro referente íÆ’í†â€™íâ€ší‚Â  Venda "
                + saleNumber + ".",
            saleNumber);
    }

    private Map<String, Object> buildPayload(
            ProcessedReceipt receipt,
            String errorMessage,
            String channel,
            boolean includeRetryError,
            String saleNumber) {
        Map<String, Object> payload = new HashMap<>(receipt.getPayload() != null ? receipt.getPayload() : Map.of());

        payload.put("retryCount", receipt.getRetryCount());
        payload.put("channel", channel);
        payload.put("updatedAt", LocalDateTime.now().toString());

        if (StringUtils.hasText(saleNumber)) {
            // MantíÆ’í†â€™íâ€ší‚Â©m o níÆ’í†â€™íâ€ší‚Âºmero comercial disponíÆ’í†â€™íâ€ší‚Â­vel para UI/DTO sem perder a chave UUID interna.
            String normalizedSaleNumber = saleNumber.trim();
            payload.put("numero", normalizedSaleNumber);
            payload.put("numero_venda", normalizedSaleNumber);
            payload.put("saleNumber", normalizedSaleNumber);
            payload.put("displayIdentifier", normalizedSaleNumber);
        } else if (!payload.containsKey("displayIdentifier") && StringUtils.hasText(receipt.getParcelaId())) {
            payload.put("displayIdentifier", receipt.getParcelaId());
        }

        if (includeRetryError && errorMessage != null) {
            payload.put("lastError", errorMessage);
        }

        return payload;
    }

    private FinancialLink resolveFinancialLink(ContaAzulPaymentParcel parcela) {
        if (StringUtils.hasText(parcela.customerId())) {
            return financialLinkRepository
                    .findByContaAzulCustomerId(parcela.customerId())
                    .orElseGet(() -> autoCreateFinancialLink(parcela));
        }

        return financialLinkRepository
                .findByContaAzulCustomerNameIgnoreCase(parcela.medicoNome())
                .orElseThrow(() -> new IllegalStateException(
                        "FinancialLink níÆ’í†â€™íâ€ší‚Â£o encontrado e customerId ausente para: "
                                + parcela.medicoNome()));
    }

    private FinancialLink autoCreateFinancialLink(ContaAzulPaymentParcel parcela) {
        if (financialLinkRepository.existsByContaAzulCustomerIdAndCanal(
                parcela.customerId(), FinancialLink.Canal.EMAIL)) {
            return financialLinkRepository
                    .findByContaAzulCustomerIdAndCanal(
                            parcela.customerId(), FinancialLink.Canal.EMAIL)
                    .orElseThrow();
        }

        log.info("FinancialLink níÆ’í†â€™íâ€ší‚Â£o encontrado íÆ’í‚Â¢íÂ¢ââ‚¬Å¡í‚Â¬íÂ¢ââ€šÂ¬í‚Â criando automaticamente. customerId={}, nome={}",
                parcela.customerId(), parcela.medicoNome());

        FinancialLink link = FinancialLink.builder()
                .contaAzulCustomerId(parcela.customerId())
                .contaAzulCustomerName(parcela.medicoNome())
                .canal(FinancialLink.Canal.EMAIL)
                .build();

        return financialLinkRepository.save(link);
    }

    private String resolveRecipientEmail(ContaAzulPaymentParcel parcela, FinancialLink financialLink) {
        if (StringUtils.hasText(parcela.recipientEmail())) {
            return parcela.recipientEmail();
        }
        return emailSyncService.resolveEmail(financialLink);
    }
}




