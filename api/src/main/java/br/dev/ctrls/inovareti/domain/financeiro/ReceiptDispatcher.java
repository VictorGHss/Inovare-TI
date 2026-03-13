package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.notification.FinanceEmailService;
import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceiptDispatcher {

    private final FinancialLinkRepository financialLinkRepository;
    private final ProcessedReceiptRepository processedReceiptRepository;
    private final FinanceEmailService financeEmailService;
    private final DiscordDirectMessageService discordDirectMessageService;

    public void dispatchReceipt(ContaAzulPaymentParcel parcela, byte[] pdfBytes, String receiptHash) {
        FinancialLink financialLink = financialLinkRepository.findByContaAzulCustomerId(parcela.customerId())
                .orElseThrow(() -> new IllegalStateException("FinancialLink not found for ContaAzul customer: " + parcela.customerId()));

        ProcessedReceipt receipt = processedReceiptRepository.findByParcelaId(parcela.parcelaId())
                .orElseGet(ProcessedReceipt::new);

        receipt.setFinancialLink(financialLink);
        receipt.setParcelaId(parcela.parcelaId());
        receipt.setReceiptHash(receiptHash);
        receipt.setOriginalRecipientEmail(resolveRecipientEmail(parcela, financialLink));

        try {
            routeReceipt(parcela, financialLink, receipt, pdfBytes);
            receipt.setStatus(ProcessedReceiptStatus.SENT);
            receipt.setRetryCount(0);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setPayload(buildPayload(receipt, null, financialLink.getNotificationChannel().name(), false));
            processedReceiptRepository.save(receipt);
        } catch (RuntimeException ex) {
            int nextRetryCount = receipt.getRetryCount() + 1;
            receipt.setStatus(ProcessedReceiptStatus.PENDING_RETRY);
            receipt.setRetryCount(nextRetryCount);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setPayload(buildPayload(receipt, ex.getMessage(), financialLink.getNotificationChannel().name(), true));
            processedReceiptRepository.save(receipt);
            throw ex;
        }
    }

    public ProcessedReceipt markRetryFailure(ProcessedReceipt receipt, String errorMessage) {
        int nextRetryCount = receipt.getRetryCount() + 1;
        receipt.setStatus(ProcessedReceiptStatus.PENDING_RETRY);
        receipt.setRetryCount(nextRetryCount);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, errorMessage, receipt.getFinancialLink().getNotificationChannel().name(), true));
        return processedReceiptRepository.save(receipt);
    }

    public ProcessedReceipt markPermanentFailure(ProcessedReceipt receipt, String errorMessage) {
        receipt.setStatus(ProcessedReceiptStatus.FAILED);
        receipt.setProcessedAt(LocalDateTime.now());
        receipt.setPayload(buildPayload(receipt, errorMessage, receipt.getFinancialLink().getNotificationChannel().name(), false));
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
            byte[] pdfBytes) {
        if (financialLink.getNotificationChannel() == FinancialNotificationChannel.DISCORD) {
            discordDirectMessageService.sendFinancialReceiptNotification(
                    financialLink.getUser().getDiscordUserId(),
                    parcela.medicoNome(),
                    parcela.parcelaId());
            return;
        }

        financeEmailService.sendReceiptEmailWithPdf(
                parcela.medicoNome(),
                receipt.getOriginalRecipientEmail(),
                "Olá " + parcela.medicoNome() + ", seu recibo financeiro está em anexo.",
                pdfBytes,
                "recibo-" + parcela.parcelaId() + ".pdf");
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

    private String resolveRecipientEmail(ContaAzulPaymentParcel parcela, FinancialLink financialLink) {
        if (StringUtils.hasText(parcela.recipientEmail())) {
            return parcela.recipientEmail();
        }

        return financialLink.getUser().getEmail();
    }
}
