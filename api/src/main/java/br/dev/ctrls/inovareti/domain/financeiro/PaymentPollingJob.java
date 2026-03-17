package br.dev.ctrls.inovareti.domain.financeiro;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final int PAGE_SIZE = 50;

    private final ContaAzulTokenService contaAzulTokenService;
    private final ContaAzulPaymentsClient paymentsClient;
    private final ProcessedReceiptRepository processedReceiptRepository;
    private final ReceiptDispatcher receiptDispatcher;
    private final SystemSettingRepository systemSettingRepository;

    @Value("${app.financeiro.polling.fallback-hours:24}")
    private long pollingFallbackHours;

    @Value("${app.financeiro.polling.minimum-lookback-minutes:30}")
    private long minimumLookbackMinutes;

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void pollPaidParcels() {
        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.info("ContaAzul não autorizado, pulando polling.");
            return;
        }

        LocalDateTime pollingAte = LocalDateTime.now();
        LocalDateTime pollingDe = resolvePollingStart(pollingAte);

        try {
            PollingProcessingResult result = processPaidParcelsWindow(pollingDe, pollingAte, "scheduled");
            log.info(
                    "Polling financeiro concluído: fetched={}, processed={}, skippedAlreadyProcessed={}, failures={}, janela=[{} -> {}]",
                    result.totalFetched(),
                    result.totalProcessed(),
                    result.skippedAlreadyProcessed(),
                    result.failures(),
                    pollingDe,
                    pollingAte);
        } catch (IllegalStateException ex) {
            log.info("ContaAzul não autorizado, pulando polling.");
            return;
        }

        markLastSuccessfulPollingAt(pollingAte);
    }

    public PollingProcessingResult reprocessWindow(LocalDateTime from, LocalDateTime to) {
        if (!contaAzulTokenService.hasAuthorizedToken()) {
            throw new IllegalStateException("ContaAzul não autorizado para reprocessamento manual.");
        }

        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("Janela de reprocessamento inválida. Informe from < to.");
        }

        PollingProcessingResult result = processPaidParcelsWindow(from, to, "manual-reprocess");
        log.info(
                "Reprocessamento manual concluído: fetched={}, processed={}, skippedAlreadyProcessed={}, failures={}, janela=[{} -> {}]",
                result.totalFetched(),
                result.totalProcessed(),
                result.skippedAlreadyProcessed(),
                result.failures(),
                from,
                to);
        return result;
    }

    private PollingProcessingResult processPaidParcelsWindow(LocalDateTime from, LocalDateTime to, String source) {
        int totalFetched = 0;
        int totalProcessed = 0;
        int skippedAlreadyProcessed = 0;
        int failures = 0;
        int page = 1;
        List<String> processedParcelIds = new ArrayList<>();

        while (true) {
            List<ContaAzulPaymentParcel> paidParcels = paymentsClient.fetchPaidParcelsSinceLastRun(from, to, PAGE_SIZE, page);
            if (paidParcels.isEmpty()) {
                break;
            }

            totalFetched += paidParcels.size();

            for (ContaAzulPaymentParcel parcela : paidParcels) {
                if (processedReceiptRepository.existsByParcelaId(parcela.parcelaId())) {
                    skippedAlreadyProcessed++;
                    continue;
                }

                try {
                    byte[] pdfBytes = paymentsClient.downloadReceiptPdf(parcela.parcelaId());
                    String receiptHash = sha256(pdfBytes);
                    receiptDispatcher.dispatchReceipt(parcela, pdfBytes, receiptHash);
                    totalProcessed++;
                    processedParcelIds.add(parcela.parcelaId());
                } catch (RuntimeException ex) {
                    failures++;
                    log.error("Falha no processamento da parcela {} durante {}.", parcela.parcelaId(), source, ex);
                }
            }

            if (paidParcels.size() < PAGE_SIZE) {
                break;
            }

            page++;
        }

        return new PollingProcessingResult(totalFetched, totalProcessed, skippedAlreadyProcessed, failures, processedParcelIds);
    }

    private LocalDateTime resolvePollingStart(LocalDateTime pollingAte) {
        LocalDateTime minimumWindowStart = pollingAte.minusMinutes(minimumLookbackMinutes);

        return readLastSuccessfulPollingAt()
                .map(lastSuccessfulPollingAt -> lastSuccessfulPollingAt.isBefore(minimumWindowStart)
                        ? lastSuccessfulPollingAt
                        : minimumWindowStart)
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

    public record PollingProcessingResult(
            int totalFetched,
            int totalProcessed,
            int skippedAlreadyProcessed,
            int failures,
            List<String> processedParcelIds) {
    }
}
