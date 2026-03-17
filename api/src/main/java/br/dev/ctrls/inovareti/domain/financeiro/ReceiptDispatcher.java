package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.notification.FinanceEmailService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
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
        receipt.setOriginalRecipientEmail(resolveRecipientEmail(parcela));

        try {
            routeReceipt(parcela, financialLink, receipt, pdfBytes, includePdfAttachment);
            receipt.setStatus(ProcessedReceiptStatus.SENT);
            receipt.setRetryCount(0);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setPayload(buildPayload(receipt, null, financialLink.getCanal().name(), false));
            processedReceiptRepository.save(receipt);
        } catch (RuntimeException ex) {
            int nextRetryCount = receipt.getRetryCount() + 1;
            receipt.setStatus(ProcessedReceiptStatus.PENDING_RETRY);
            receipt.setRetryCount(nextRetryCount);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setPayload(buildPayload(receipt, ex.getMessage(), financialLink.getCanal().name(), true));
            processedReceiptRepository.save(receipt);
            throw ex;
        }
    }

    public ProcessedReceipt markRetryFailure(ProcessedReceipt receipt, String errorMessage) {
        int nextRetryCount = receipt.getRetryCount() + 1;
        receipt.setStatus(ProcessedReceiptStatus.PENDING_RETRY);
        receipt.setRetryCount(nextRetryCount);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, errorMessage, receipt.getFinancialLink().getCanal().name(), true));
        return processedReceiptRepository.save(receipt);
    }

    public ProcessedReceipt markPermanentFailure(ProcessedReceipt receipt, String errorMessage) {
        receipt.setStatus(ProcessedReceiptStatus.FAILED);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, errorMessage, receipt.getFinancialLink().getCanal().name(), false));
        return processedReceiptRepository.save(receipt);
    }

    public ProcessedReceipt saveHistoricalReceipt(ProcessedReceipt receipt, String channel) {
        receipt.setStatus(ProcessedReceiptStatus.HISTORICO);
        receipt.setRetryCount(0);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, null, channel, false));
        return processedReceiptRepository.save(receipt);
    }

    private void routeReceipt(
            ContaAzulPaymentParcel parcela,
            FinancialLink financialLink,
            ProcessedReceipt receipt,
            byte[] pdfBytes,
            boolean includePdfAttachment) {
            if (financialLink.getCanal() == FinancialLink.Canal.DISCORD) {
                throw new IllegalStateException(
                    "Canal DISCORD não possui destinatário vinculado no financial_link para customerId="
                        + parcela.customerId());
        }

        if (includePdfAttachment && pdfBytes != null && pdfBytes.length > 0) {
            financeEmailService.sendReceiptEmailWithPdf(
                parcela.medicoNome(),
                receipt.getOriginalRecipientEmail(),
                "Olá " + parcela.medicoNome() + ", seu recibo financeiro está em anexo.",
                pdfBytes,
                "recibo-" + parcela.parcelaId() + ".pdf");
            return;
        }

        financeEmailService.sendReceiptEmail(
            parcela.medicoNome(),
            receipt.getOriginalRecipientEmail(),
            "Olá " + parcela.medicoNome() + ", registramos o recebimento financeiro da parcela "
                + parcela.parcelaId() + ".");
    }

    private Map<String, Object> buildPayload(
            ProcessedReceipt receipt,
            String errorMessage,
            String channel,
            boolean includeRetryError) {
        Map<String, Object> payload = new HashMap<>(receipt.getPayload() != null ? receipt.getPayload() : Map.of());

        payload.put("retryCount", receipt.getRetryCount());
        payload.put("channel", channel);
        payload.put("updatedAt", LocalDateTime.now().toString());

        if (includeRetryError && errorMessage != null) {
            payload.put("lastError", errorMessage);
        }

        return payload;
    }

    private FinancialLink resolveFinancialLink(ContaAzulPaymentParcel parcela) {
        if (StringUtils.hasText(parcela.customerId())) {
            return financialLinkRepository.findByContaAzulCustomerId(parcela.customerId())
                    .orElseGet(() -> resolveFinancialLinkByName(parcela));
        }

        return resolveFinancialLinkByName(parcela);
    }

    private FinancialLink resolveFinancialLinkByName(ContaAzulPaymentParcel parcela) {
        if (StringUtils.hasText(parcela.medicoNome())) {
            return financialLinkRepository.findByContaAzulCustomerNameIgnoreCase(parcela.medicoNome())
                    .orElseThrow(() -> new IllegalStateException(
                            "FinancialLink not found for ContaAzul parcel [customerId=" + parcela.customerId()
                                    + ", medicoNome=" + parcela.medicoNome() + "]"));
        }

        throw new IllegalStateException("FinancialLink not found for ContaAzul parcel without customerId and medicoNome.");
    }

    private String resolveRecipientEmail(ContaAzulPaymentParcel parcela) {
        if (StringUtils.hasText(parcela.recipientEmail())) {
            return parcela.recipientEmail();
        }

        FinancialLink link = financialLinkRepository
                .findByContaAzulCustomerIdAndCanal(parcela.customerId(), FinancialLink.Canal.EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "FinancialLink não encontrado para customerId=" + parcela.customerId()));

        return emailSyncService.resolveEmail(link);
    }
}
