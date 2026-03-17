package br.dev.ctrls.inovareti.domain.financeiro;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentsClient;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulTokenService;
import br.dev.ctrls.inovareti.domain.settings.SystemSetting;
import br.dev.ctrls.inovareti.domain.settings.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPollingJob {

    private static final String LAST_SUCCESSFUL_POLLING_AT_KEY = "financeiro.polling.last_successful_at";

    private final ContaAzulTokenService contaAzulTokenService;
    private final ContaAzulPaymentsClient paymentsClient;
    private final ProcessedReceiptRepository processedReceiptRepository;
    private final ReceiptDispatcher receiptDispatcher;
    private final SystemSettingRepository systemSettingRepository;

    @Value("${app.financeiro.polling.fallback-hours:24}")
    private long pollingFallbackHours;

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void pollPaidParcels() {
        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.info("ContaAzul não autorizado, pulando polling.");
            return;
        }

        LocalDateTime pollingAte = LocalDateTime.now();
        LocalDateTime pollingDe = resolvePollingStart(pollingAte);

        List<ContaAzulPaymentParcel> paidParcels;
        try {
            paidParcels = paymentsClient.fetchPaidParcelsSinceLastRun(pollingDe, pollingAte, 50, 1);
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

        markLastSuccessfulPollingAt(pollingAte);
    }

    private LocalDateTime resolvePollingStart(LocalDateTime pollingAte) {
        return readLastSuccessfulPollingAt()
                .orElseGet(() -> pollingAte.minusHours(pollingFallbackHours));
    }

    private Optional<LocalDateTime> readLastSuccessfulPollingAt() {
        return systemSettingRepository.findById(LAST_SUCCESSFUL_POLLING_AT_KEY)
                .map(SystemSetting::getValue)
                .flatMap(this::parsePollingTimestamp);
    }

    private Optional<LocalDateTime> parsePollingTimestamp(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDateTime.parse(rawValue));
        } catch (RuntimeException ex) {
            log.warn("Timestamp inválido em system_settings[{}]='{}'. Usando fallback de {}h.",
                    LAST_SUCCESSFUL_POLLING_AT_KEY,
                    rawValue,
                    pollingFallbackHours);
            return Optional.empty();
        }
    }

    private void markLastSuccessfulPollingAt(LocalDateTime timestamp) {
        SystemSetting setting = systemSettingRepository.findById(LAST_SUCCESSFUL_POLLING_AT_KEY)
                .orElseGet(() -> SystemSetting.builder()
                        .id(LAST_SUCCESSFUL_POLLING_AT_KEY)
                        .description("Última execução bem-sucedida do polling financeiro (LocalDateTime ISO-8601)")
                        .build());

        setting.setValue(timestamp.toString());
        systemSettingRepository.save(setting);
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
