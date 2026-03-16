package br.dev.ctrls.inovareti.domain.financeiro;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentsClient;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPollingJob {

    private final ContaAzulTokenService contaAzulTokenService;
    private final ContaAzulPaymentsClient paymentsClient;
    private final ProcessedReceiptRepository processedReceiptRepository;
    private final ReceiptDispatcher receiptDispatcher;

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void pollPaidParcels() {
        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.info("ContaAzul não autorizado, pulando polling.");
            return;
        }

        List<ContaAzulPaymentParcel> paidParcels;
        try {
            paidParcels = paymentsClient.fetchPaidParcelsFromLastSixHours();
        } catch (IllegalStateException ex) {
            log.info("ContaAzul não autorizado, pulando polling.");
            return;
        }

        for (ContaAzulPaymentParcel parcela : paidParcels) {
            if (processedReceiptRepository.existsByParcelaId(parcela.parcelaId())) {
                continue;
            }

            try {
                byte[] pdfBytes = paymentsClient.downloadReceiptPdf(parcela.parcelaId());
                String receiptHash = sha256(pdfBytes);
                receiptDispatcher.dispatchReceipt(parcela, pdfBytes, receiptHash);
            } catch (RuntimeException ex) {
                log.error("Falha no processamento da parcela {} durante polling financeiro.", parcela.parcelaId(), ex);
            }
        }
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
