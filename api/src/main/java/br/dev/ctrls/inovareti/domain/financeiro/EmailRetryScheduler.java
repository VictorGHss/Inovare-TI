package br.dev.ctrls.inovareti.domain.financeiro;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private static final int MAX_RETRIES = 3;

    private final ProcessedReceiptRepository processedReceiptRepository;
    private final ReceiptDispatcher receiptDispatcher;
    private final AlertService alertService;
    private final ContaAzulPaymentsClient paymentsClient;

    @Scheduled(fixedDelay = 900_000L, initialDelay = 240_000L)
    public void retryPendingReceipts() {
        for (ProcessedReceipt receipt : processedReceiptRepository.findByStatus(ProcessedReceiptStatus.PENDING_RETRY)) {
            int retries = readRetryCount(receipt);
            if (retries >= MAX_RETRIES) {
                handlePermanentFailure(receipt, "Limite de tentativas de reenvio excedido.");
                continue;
            }

            try {
                byte[] pdf = paymentsClient.downloadReceiptPdf(receipt.getParcelaId());
                String receiptHash = sha256(pdf);

                ContaAzulPaymentParcel parcel = new ContaAzulPaymentParcel(
                        receipt.getParcelaId(),
                        receipt.getFinancialLink().getContaAzulCustomerId(),
                        receipt.getFinancialLink().getContaAzulCustomerName() != null
                                ? receipt.getFinancialLink().getContaAzulCustomerName()
                                : receipt.getFinancialLink().getUser().getName(),
                        receipt.getOriginalRecipientEmail());

                receiptDispatcher.dispatchReceipt(parcel, pdf, receiptHash);
            } catch (RuntimeException ex) {
                int nextAttempt = retries + 1;
                if (nextAttempt >= MAX_RETRIES) {
                    handlePermanentFailure(receipt, ex.getMessage());
                } else {
                    receiptDispatcher.markRetryFailure(receipt, ex.getMessage());
                    log.warn("Retry financeiro {}/{} falhou para parcela {}",
                            nextAttempt,
                            MAX_RETRIES,
                            receipt.getParcelaId());
                }
            }
        }
    }

    private void handlePermanentFailure(ProcessedReceipt receipt, String reason) {
        receiptDispatcher.markPermanentFailure(receipt, reason);

        alertService.registerPermanentFailure(
                receipt.getParcelaId(),
                reason,
                Map.of(
                        "parcelaId", receipt.getParcelaId(),
                        "financialLinkId", receipt.getFinancialLink().getId().toString(),
                        "timestamp", LocalDateTime.now().toString()));
    }

    private int readRetryCount(ProcessedReceipt receipt) {
        Object retryCountValue = receipt.getPayload() != null
                ? receipt.getPayload().getOrDefault("retryCount", 0)
                : 0;

        if (retryCountValue instanceof Number number) {
            return number.intValue();
        }

        return 0;
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content != null ? content : new byte[0]);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Falha ao calcular hash do recibo.", ex);
        }
    }
}
