package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.model.TesteEnvioRealResult;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulHttpException;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulClient;
import br.dev.ctrls.inovareti.modules.finance.domain.model.NoReceiptAvailableException;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ReceiptConcurrencyHandler;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulReceiptValidator;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulReceiptStorageAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.DoctorEmailMapping;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço que atua como orquestrador do fluxo da camada de aplicação para processamento de recibos quitados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class ContaAzulReceiptProcessor {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern SALE_NUMBER_PATTERN = Pattern.compile("(?i)(?:numero_venda|numero|venda)\\s*[:#-]?\\s*(\\d{3,})");

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final ReceiptEmailService receiptEmailService;
    private final ContaAzulProperties properties;
    private final ContaAzulReceiptStorageAdapter storageAdapter;
    private final ContaAzulReceiptValidator validator;
    private final InternalReceiptEmissionService fallbackService;
    private final ReceiptConcurrencyHandler concurrencyHandler;
    private final ReceiptAuditLogService auditService;

    private record SaleDetails(String customerUuid, String resolvedSaleId, String resolvedSaleNumber, String saleDescription) {}
    private record ReceiptPdfResult(byte[] pdfBytes, boolean usedInternalFallback, boolean isNoAttachment, String errorMessage) {}
    private record ProcessResult(boolean success, boolean skippedProcessed, boolean skippedMapping, boolean isNoAttachment, boolean isMappingWarning) {}

    @PostConstruct
    public void logContaAzulAutomationConfigOnBoot() {
        log.info("Diagnóstico Conta Azul no boot: app.contaazul.api-v2-base-url={}", properties.getApiV2BaseUrl());
    }

    public TesteEnvioRealResult processRealSaleTest(String saleId) {
        log.info("Iniciando teste real para venda {}...", saleId);
        if (!contaAzulClient.hasSalesConfiguration() || !contaAzulTokenService.hasAuthorizedToken()) {
            throw new IllegalStateException("Configuração ou token incompleto para teste real.");
        }

        ContaAzulClient.SaleItem sale = contaAzulClient.findAcquittedSaleById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venda não encontrada com status ACQUITTED: " + saleId));

        ContaAzulReceiptValidator.ValidationResult validationResult = validator.validate(sale);
        if (validationResult.isNotValid()) {
            throw new IllegalStateException(validationResult.errorMessage());
        }

        DoctorEmailMapping mapping = validationResult.mapping();
        String recipientEmail = validationResult.recipientEmail();
        String doctorName = validator.resolveDoctorName(mapping, sale.customerName());

        String resolvedSaleId = resolveSaleIdForReceiptFlow(sale);
        String resolvedSaleNumber = resolveSaleNumberForReceiptFlow(sale, resolvedSaleId);

        String baixaId = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId())
                .map(s -> s.trim())
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalStateException("Nenhuma baixa válida encontrada."));

        byte[] pdfBytes = storageAdapter.downloadReceiptPdf(baixaId);
        receiptEmailService.sendReceiptForRealSaleTest(doctorName, recipientEmail, resolvedSaleNumber, pdfBytes);
        return new TesteEnvioRealResult(resolvedSaleId, doctorName, recipientEmail, pdfBytes.length);
    }

    public void processCurrentMonthAcquittedSales() {
        LocalDate hoje = LocalDate.now();
        LocalDate primeiroDiaMesAtual = hoje.withDayOfMonth(1);
        processAcquittedSales(primeiroDiaMesAtual, hoje);
    }

    public ReceiptProcessingResult processAcquittedSales(LocalDate dataInicio, LocalDate dataFim) {
        if (!properties.getAutomation().isEnabled() || !contaAzulClient.hasSalesConfiguration() || !contaAzulTokenService.hasAuthorizedToken()) {
            return ReceiptProcessingResult.empty();
        }

        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)) {
            return ReceiptProcessingResult.empty();
        }

        log.info("Automação ContaAzul: consultando parcelas recebidas no período: {} a {}", dataInicio, dataFim);
        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales(dataInicio.format(DATE_FORMATTER), dataFim.format(DATE_FORMATTER));
        } catch (RuntimeException ex) {
            return handleFetchException(ex);
        }

        return executeProcessingLoop(acquittedSales);
    }

    private ReceiptProcessingResult handleFetchException(RuntimeException ex) {
        if (ex instanceof ContaAzulHttpException httpEx && httpEx.isStatus(403) && isPlanIneligibleResponse(httpEx.getResponseBody())) {
            String msg = "Conta Azul indisponível: conta sem elegibilidade de API (END_TRIAL).";
            log.warn(msg);
            return new ReceiptProcessingResult(0, 0, 0, 0, 0, 0, 0, List.of(msg));
        }
        return new ReceiptProcessingResult(0, 0, 0, 0, 1, 0, 0, List.of("Falha ao buscar vendas: " + ex.getMessage()));
    }

    private ReceiptProcessingResult executeProcessingLoop(List<ContaAzulClient.SaleItem> acquittedSales) {
        int sent = 0;
        int skippedProcessed = 0;
        int skippedMapping = 0;
        int failures = 0;
        int noAttachmentWarnings = 0;
        int mappingWarnings = 0;
        List<String> errors = new ArrayList<>();

        for (ContaAzulClient.SaleItem sale : acquittedSales) {
            try {
                ProcessResult result = processSingleSale(sale, errors);
                if (result.success()) sent++;
                if (result.skippedProcessed()) skippedProcessed++;
                if (result.skippedMapping()) skippedMapping++;
                if (!result.success() && !result.skippedProcessed() && !result.skippedMapping()) failures++;
                if (result.isNoAttachment()) noAttachmentWarnings++;
                if (result.isMappingWarning()) mappingWarnings++;
            } catch (DataIntegrityViolationException ex) {
                skippedProcessed++;
            } catch (RuntimeException ex) {
                failures++;
                errors.add(ex.getMessage());
            }
        }
        return new ReceiptProcessingResult(acquittedSales.size(), sent, skippedProcessed, skippedMapping, failures, noAttachmentWarnings, mappingWarnings, List.copyOf(errors));
    }

    private ProcessResult processSingleSale(ContaAzulClient.SaleItem sale, List<String> errors) {
        if (!applyThrottle()) return new ProcessResult(false, false, false, false, false);
        auditService.logStart(sale.descricao(), sale.origem(), sale.parcelaId());

        SaleDetails details = resolveSaleDetails(sale);
        Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
        if (baixaIdOpt.isEmpty()) {
            auditService.recordNoBaixaFound(sale.parcelaId(), details.resolvedSaleId(), errors);
            return new ProcessResult(false, false, false, false, false);
        }

        String baixaId = baixaIdOpt.get().trim();
        if (!StringUtils.hasText(baixaId)) {
            auditService.recordInvalidBaixaId(sale.parcelaId(), errors);
            return new ProcessResult(false, false, false, false, false);
        }

        if (concurrencyHandler.isAlreadyProcessed(baixaId) || !concurrencyHandler.acquireLock(baixaId)) {
            return new ProcessResult(false, true, false, false, false);
        }

        try {
            return processLockedSale(sale, details, baixaId, errors);
        } finally {
            concurrencyHandler.releaseLock(baixaId);
        }
    }

    private ProcessResult processLockedSale(ContaAzulClient.SaleItem sale, SaleDetails details, String baixaId, List<String> errors) {
        ContaAzulReceiptValidator.ValidationResult validationResult = validator.validate(sale);
        if (validationResult.isNotValid()) {
            auditService.registerError(errors, validationResult.errorMessage());
            return new ProcessResult(false, false, true, false, true);
        }

        DoctorEmailMapping mapping = validationResult.mapping();
        String recipientEmail = validationResult.recipientEmail();
        String doctorName = validator.resolveDoctorName(mapping, sale.customerName());

        ReceiptPdfResult pdfResult = downloadOrGenerateReceipt(baixaId, doctorName, mapping, details, sale.idReciboDigital());
        if (pdfResult.pdfBytes() == null || pdfResult.pdfBytes().length == 0) {
            auditService.registerError(errors, pdfResult.errorMessage() != null ? pdfResult.errorMessage() : "PDF vazio.");
            return new ProcessResult(false, false, false, pdfResult.isNoAttachment(), false);
        }

        if (!sendReceiptEmail(doctorName, recipientEmail, details.resolvedSaleNumber(), baixaId, pdfResult.pdfBytes())) {
            auditService.registerError(errors, "Erro no envio de e-mail.");
            return new ProcessResult(false, false, false, pdfResult.isNoAttachment(), false);
        }

        auditService.recordSuccess(baixaId, doctorName, pdfResult.usedInternalFallback(), sale.customerName());
        return new ProcessResult(true, false, false, pdfResult.isNoAttachment(), false);
    }

    private SaleDetails resolveSaleDetails(ContaAzulClient.SaleItem sale) {
        String customerUuid = validator.normalizeUuid(sale.customerUuid());
        String resolvedSaleId = resolveSaleIdForReceiptFlow(sale);
        String resolvedSaleNumber = resolveSaleNumberForReceiptFlow(sale, resolvedSaleId);
        String saleDescription = resolveSaleDescriptionForReceiptFlow(sale, resolvedSaleNumber);
        return new SaleDetails(customerUuid, resolvedSaleId, resolvedSaleNumber, saleDescription);
    }

    private ReceiptPdfResult downloadOrGenerateReceipt(String baixaId, String doctorName, DoctorEmailMapping mapping, SaleDetails details, String idReciboDigital) {
        if (!StringUtils.hasText(idReciboDigital)) {
            return generateFallbackPdf(baixaId, doctorName, mapping, details);
        }
        try {
            byte[] pdfBytes = storageAdapter.downloadReceiptPdf(baixaId);
            return new ReceiptPdfResult(pdfBytes, false, false, null);
        } catch (NoReceiptAvailableException nr) {
            return generateFallbackPdf(baixaId, doctorName, mapping, details);
        } catch (RuntimeException ex) {
            return new ReceiptPdfResult(null, false, false, "Download falhou: " + ex.getMessage());
        }
    }

    private ReceiptPdfResult generateFallbackPdf(String baixaId, String doctorName, DoctorEmailMapping mapping, SaleDetails details) {
        try {
            byte[] pdfBytes = fallbackService.generateInternalReceiptPdf(baixaId, doctorName, mapping, details.customerUuid(), details.saleDescription());
            return new ReceiptPdfResult(pdfBytes, true, true, null);
        } catch (RuntimeException fallbackEx) {
            return new ReceiptPdfResult(null, false, true, "Fallback falhou: " + fallbackEx.getMessage());
        }
    }

    private boolean sendReceiptEmail(String doctorName, String recipientEmail, String resolvedSaleNumber, String baixaId, byte[] pdfBytes) {
        try {
            receiptEmailService.sendReceiptForBaixa(doctorName, recipientEmail, resolvedSaleNumber, baixaId, pdfBytes);
            return true;
        } catch (RuntimeException emailEx) {
            log.error("Falha ao enviar e-mail do recibo para a baixa: {}", baixaId, emailEx);
            return false;
        }
    }

    private String resolveSaleIdForReceiptFlow(ContaAzulClient.SaleItem sale) {
        String fallbackSaleId = StringUtils.hasText(sale.saleId()) ? sale.saleId().trim() : null;
        if (!"VENDA".equalsIgnoreCase(sale.origem()) || !StringUtils.hasText(sale.parcelaId())) {
            return fallbackSaleId;
        }
        try {
            Optional<ContaAzulClient.ParcelaDetailDTO> detailOpt = contaAzulClient.fetchParcelaDetail(sale.parcelaId());
            if (detailOpt.isPresent() && StringUtils.hasText(detailOpt.get().saleId())) {
                return detailOpt.get().saleId().trim();
            }
        } catch (RuntimeException ex) {
            log.warn("Não foi possível resolver sale_id pela parcela {}.", sale.parcelaId(), ex);
        }
        return fallbackSaleId;
    }

    private boolean isPlanIneligibleResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) return false;
        String normalized = responseBody.toUpperCase();
        return normalized.contains("END_TRIAL") || normalized.contains("NAO ESTA ELEGIVEL");
    }

    private String resolveSaleNumberForReceiptFlow(ContaAzulClient.SaleItem sale, String resolvedSaleId) {
        if (StringUtils.hasText(sale.saleNumber()) && sale.saleNumber().trim().matches("\\d{3,}")) {
            return sale.saleNumber().trim();
        }
        String fromDescription = extractSaleNumber(sale.descricao());
        if (StringUtils.hasText(fromDescription)) return fromDescription;
        String fromResolvedSaleId = extractSaleNumber(resolvedSaleId);
        return StringUtils.hasText(fromResolvedSaleId) ? fromResolvedSaleId : "N/D";
    }

    private String resolveSaleDescriptionForReceiptFlow(ContaAzulClient.SaleItem sale, String resolvedSaleNumber) {
        if (StringUtils.hasText(resolvedSaleNumber) && !"N/D".equalsIgnoreCase(resolvedSaleNumber)) {
            return "Venda " + resolvedSaleNumber;
        }
        return StringUtils.hasText(sale.descricao()) ? sale.descricao().trim() : "Venda N/D";
    }

    private String extractSaleNumber(String rawValue) {
        if (!StringUtils.hasText(rawValue)) return null;
        String normalized = rawValue.trim();
        if (normalized.matches("\\d{3,}")) return normalized;
        Matcher matcher = SALE_NUMBER_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group(1) : null;
    }

    public record ReceiptProcessingResult(int acquitted, int sent, int skippedProcessed, int skippedMapping, int failures, int noAttachmentWarnings, int mappingWarnings, List<String> errors) {
        static ReceiptProcessingResult empty() {
            return new ReceiptProcessingResult(0, 0, 0, 0, 0, 0, 0, List.of());
        }
    }

    private boolean applyThrottle() {
        LockSupport.parkNanos(350_000_000L);
        return !Thread.currentThread().isInterrupted();
    }
}


