package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialLinkRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceipt;
import br.dev.ctrls.inovareti.modules.finance.application.service.ReceiptDispatcher;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedReceiptRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.application.service.CustomerEmailSyncService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceiptStatus;
import br.dev.ctrls.inovareti.modules.finance.domain.port.SystemAlertRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialLink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceiroOperationsService {

    private static final int BACKFILL_DAYS = 30;
    private static final int WINDOW_DAYS = 7;
    private static final int PAGE_SIZE = 50;
    private static final long WINDOW_DELAY_MS = 2000L;

    private final ProcessedReceiptRepository processedReceiptRepository;
    private final SystemAlertRepository systemAlertRepository;
    private final FinancialLinkRepository financialLinkRepository;
    private final ContaAzulPaymentsClient contaAzulPaymentsClient;
    private final CustomerEmailSyncService emailSyncService;
    private final ReceiptDispatcher receiptDispatcher;

    @Transactional(readOnly = true)
    public List<ProcessedReceipt> listReceipts() {
        return processedReceiptRepository.findAllByOrderByProcessedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SystemAlert> listAlerts() {
        return systemAlertRepository.findAllByOrderByCreatedAtDesc();
    }

    public ParcelProcessingResult conciliarParcelaPorId(String parcelaId) { // Renomeado para refletir linguagem ubÃƒÂ­qua
        if (!StringUtils.hasText(parcelaId)) {
            throw new BadRequestException("Parcela invÃƒÂ¡lida para processamento manual.");
        }

        if (processedReceiptRepository.existsByParcelaId(parcelaId)) {
            return new ParcelProcessingResult(parcelaId, false, true, "Parcela jÃƒÂ¡ processada anteriormente.");
        }

        ContaAzulPaymentParcel parcela = contaAzulPaymentsClient.fetchParcelById(parcelaId);
        try {
            byte[] pdfBytes = contaAzulPaymentsClient.downloadReceiptPdf(parcela.parcelaId());
            String receiptHash = sha256(pdfBytes);
            receiptDispatcher.dispatchReceipt(parcela, pdfBytes, receiptHash);
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn(
                    "Recibo PDF nÃƒÂ£o encontrado na ContaAzul para parcela {}. Processando sem anexo.",
                    parcela.parcelaId());
            String receiptHash = sha256("manual-no-pdf:" + parcela.parcelaId());
            receiptDispatcher.dispatchReceiptWithoutPdf(parcela, receiptHash);
        }

        return new ParcelProcessingResult(parcela.parcelaId(), true, false, "Parcela processada e recibo despachado com sucesso.");
    }

    @Transactional
    public void requeueAlertReceipt(UUID alertId, UUID resolvedBy) {
        SystemAlert alert = systemAlertRepository.findById(alertId)
                .orElseThrow(() -> new BadRequestException("Alerta financeiro nÃƒÂ£o encontrado."));

        String parcelaId = readParcelaIdFromAlert(alert);

        ProcessedReceipt receipt = processedReceiptRepository.findByParcelaId(parcelaId)
                .orElseThrow(() -> new BadRequestException("Recibo financeiro nÃƒÂ£o encontrado para a parcela informada no alerta."));

        receipt.setStatus(ProcessedReceiptStatus.PENDING_RETRY);
        receipt.setRetryCount(0);
        receipt.setProcessedAt(LocalDateTime.now());

        Map<String, Object> payload = receipt.getPayload() != null ? receipt.getPayload() : Map.of();
        receipt.setPayload(new java.util.HashMap<>(payload));
        receipt.getPayload().put("retryCount", 0);
        receipt.getPayload().put("manualRequeueAt", LocalDateTime.now().toString());
        receipt.getPayload().remove("lastError");

        processedReceiptRepository.save(receipt);

        alert.setResolved(true);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(resolvedBy);
        systemAlertRepository.save(alert);
    }

    public BackfillResult executarBackfillUltimos30Dias() { // Renomeado para refletir linguagem ubÃƒÂ­qua
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.minusDays(BACKFILL_DAYS);

        int windowsProcessed = 0;
        int totalFetched = 0;
        int totalCreated = 0;
        int skippedAlreadyProcessed = 0;
        int skippedWithoutLink = 0;

        OffsetDateTime windowStart = from;
        while (windowStart.isBefore(now)) {
            OffsetDateTime windowEnd = windowStart.plusDays(WINDOW_DAYS);
            if (windowEnd.isAfter(now)) {
                windowEnd = now;
            }

            windowsProcessed++;
            int page = 1;

            while (true) {
                List<ContaAzulPaymentParcel> parcels = contaAzulPaymentsClient.fetchPaidParcelsByWindow(
                        windowStart,
                        windowEnd,
                        PAGE_SIZE,
                        page);

                if (parcels.isEmpty()) {
                    break;
                }

                totalFetched += parcels.size();

                for (ContaAzulPaymentParcel parcel : parcels) {
                    if (processedReceiptRepository.existsByParcelaId(parcel.parcelaId())) {
                        skippedAlreadyProcessed++;
                        continue;
                    }

                    FinancialLink financialLink = financialLinkRepository.findByContaAzulCustomerId(parcel.customerId())
                            .orElse(null);

                    if (financialLink == null) {
                        skippedWithoutLink++;
                        log.warn("Backfill ignorou parcela {}: vÃƒÂ­nculo financeiro nÃƒÂ£o encontrado para customer {}.",
                                parcel.parcelaId(),
                                parcel.customerId());
                        continue;
                    }

                    ProcessedReceipt historicalReceipt = new ProcessedReceipt();
                    historicalReceipt.setFinancialLink(financialLink);
                    historicalReceipt.setParcelaId(parcel.parcelaId());
                    historicalReceipt.setReceiptHash(sha256("historico:" + parcel.parcelaId()));
                    historicalReceipt.setOriginalRecipientEmail(resolveRecipientEmail(parcel, financialLink));
                    historicalReceipt.setRetryCount(0);
                    historicalReceipt.setPayload(Map.of(
                            "source", "backfill",
                            "windowStart", windowStart.toString(),
                            "windowEnd", windowEnd.toString(),
                            "page", page,
                            "retryCount", 0));

                    receiptDispatcher.saveHistoricalReceipt(
                            historicalReceipt,
                            financialLink.getCanal().name());
                    totalCreated++;
                }

                if (parcels.size() < PAGE_SIZE) {
                    break;
                }

                page++;
            }

            windowStart = windowEnd;
            if (windowStart.isBefore(now)) {
                pauseBetweenWindows();
            }
        }

        return new BackfillResult(
                windowsProcessed,
                totalFetched,
                totalCreated,
                skippedAlreadyProcessed,
                skippedWithoutLink);
    }

    private String resolveRecipientEmail(ContaAzulPaymentParcel parcel, FinancialLink financialLink) {
        if (StringUtils.hasText(parcel.recipientEmail())) {
            return parcel.recipientEmail();
        }

        return emailSyncService.resolveEmail(financialLink);
    }

    private void pauseBetweenWindows() {
        LockSupport.parkNanos(Duration.ofMillis(WINDOW_DELAY_MS).toNanos()); // Usando LockSupport.parkNanos para delays nÃƒÂ£o bloqueantes
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Backfill interrompido durante rate limit entre janelas.");
        }
    }

    private String readParcelaIdFromAlert(SystemAlert alert) {
        if (alert.getContext() == null) {
            throw new BadRequestException("Contexto do alerta financeiro nÃƒÂ£o contÃƒÂ©m parcela associada.");
        }

        Object parcelaId = alert.getContext().get("parcelaId");
        if (!(parcelaId instanceof String parcela) || parcela.isBlank()) {
            throw new BadRequestException("Contexto do alerta financeiro invÃƒÂ¡lido para reenvio manual.");
        }

        return parcela;
    }

    private String sha256(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Falha ao calcular hash do recibo histÃƒÂ³rico.", ex);
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

    public record BackfillResult(
            int windowsProcessed,
            int totalFetched,
            int totalCreated,
            int skippedAlreadyProcessed,
            int skippedWithoutLink) {
    }

        public record ParcelProcessingResult(
            String parcelaId,
            boolean processed,
            boolean alreadyProcessed,
            String message) {
    }
}


