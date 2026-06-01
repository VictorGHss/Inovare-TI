package br.dev.ctrls.inovareti.modules.finance.application.service;

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
 * ServiÃƒÆ’Ã‚Â§o que atua como orquestrador do fluxo da camada de aplicaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o (Application Service)
 * para processamento de recibos quitados da Conta Azul.
 */
@Slf4j
@Service
@RequiredArgsConstructor
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

    @PostConstruct
    public void logContaAzulAutomationConfigOnBoot() {
        String envSalesV2 = System.getenv("CONTAAZUL_SALES_V2_URL");
        String envSalesPdf = System.getenv("CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE");

        log.info(
                "DiagnÃƒÆ’Ã‚Â³stico Conta Azul no boot: app.contaazul.api-v2-base-url={}, app.contaazul.payments-url={}, app.contaazul.sales-v2-url={}, app.contaazul.sales-pdf-v1-url-template={}, CONTAAZUL_SALES_V2_URL={}, CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE={}",
                properties.getApiV2BaseUrl(),
                properties.getPaymentsUrl(),
                StringUtils.hasText(properties.getSalesV2Url()) ? "preenchida" : "vazia",
                StringUtils.hasText(properties.getSalesPdfV1UrlTemplate()) ? "preenchida" : "vazia",
                StringUtils.hasText(envSalesV2) ? "preenchida" : "vazia",
                StringUtils.hasText(envSalesPdf) ? "preenchida" : "vazia");
    }

    public TesteEnvioRealResult processRealSaleTest(String saleId) {
        log.info("Iniciando teste real para venda {}...", saleId);

        if (!contaAzulClient.hasSalesConfiguration()) {
            throw new IllegalStateException(buildSalesConfigurationErrorMessage());
        }

        if (!contaAzulTokenService.hasAuthorizedToken()) {
            throw new IllegalStateException("Token da Conta Azul ainda nÃƒÆ’Ã‚Â£o autorizado para execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o del teste real.");
        }

        ContaAzulClient.SaleItem sale = contaAzulClient.findAcquittedSaleById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venda nÃƒÆ’Ã‚Â£o encontrada com status ACQUITTED: " + saleId));

        ContaAzulReceiptValidator.ValidationResult validationResult = validator.validate(sale);
        if (validationResult.isNotValid()) {
            throw new IllegalStateException(validationResult.errorMessage());
        }

        DoctorEmailMapping mapping = validationResult.mapping();
        String recipientEmail = validationResult.recipientEmail();
        String doctorName = validator.resolveDoctorName(mapping, sale.customerName());

        Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
        if (baixaIdOpt.isEmpty()) {
            throw new IllegalStateException("Nenhuma baixa encontrada para a parcela da venda informada.");
        }

        String resolvedSaleId = resolveSaleIdForReceiptFlow(sale);
        String resolvedSaleNumber = resolveSaleNumberForReceiptFlow(sale, resolvedSaleId);

        String baixaId = baixaIdOpt.get().trim();
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalStateException("baixaId invÃƒÆ’Ã‚Â¡lido (nulo/vazio) para a parcela da venda informada.");
        }

        log.info("Baixa ID extraÃƒÆ’Ã‚Â­do com sucesso para parcela {}: {}", sale.parcelaId(), baixaId);

        byte[] pdfBytes = storageAdapter.downloadReceiptPdf(baixaId);
        log.info("PDF baixado ({} bytes) para a venda {}.", pdfBytes.length, sale.saleId());

        log.info("Enviando para {}", recipientEmail);
        receiptEmailService.sendReceiptForRealSaleTest(
                doctorName,
                recipientEmail,
                resolvedSaleNumber,
                pdfBytes);

        log.info("Teste real finalizado com sucesso para a venda {}.", resolvedSaleNumber);
        return new TesteEnvioRealResult(resolvedSaleId, doctorName, recipientEmail, pdfBytes.length);
    }

    public void processCurrentMonthAcquittedSales() {
        LocalDate hoje = LocalDate.now();
        LocalDate primeiroDiaMesAtual = hoje.withDayOfMonth(1);
        processAcquittedSales(primeiroDiaMesAtual, hoje);
    }

    public ReceiptProcessingResult processAcquittedSales(LocalDate dataInicio, LocalDate dataFim) {
        if (!properties.getAutomation().isEnabled()) {
            log.info("Pooling Conta Azul desativado por configuraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o.");
            return ReceiptProcessingResult.empty();
        }

        if (!contaAzulClient.hasSalesConfiguration()) {
            log.warn("AutomaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o ContaAzul desabilitada: {}", buildSalesConfigurationErrorMessage());
            return ReceiptProcessingResult.empty();
        }

        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.debug("AutomaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o ContaAzul: token ainda nÃƒÆ’Ã‚Â£o autorizado. Pulando execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o.");
            return ReceiptProcessingResult.empty();
        }

        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)) {
            log.warn("PerÃƒÆ’Ã‚Â­odo invÃƒÆ’Ã‚Â¡lido para sincronizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o: dataInicio={} dataFim={}", dataInicio, dataFim);
            return ReceiptProcessingResult.empty();
        }

        log.info("AutomaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o ContaAzul: consultando endpoint de parcelas recebidas no perÃƒÆ’Ã‚Â­odo: {} a {}", dataInicio, dataFim);

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales(dataInicio.format(DATE_FORMATTER), dataFim.format(DATE_FORMATTER));
        } catch (RuntimeException ex) {
            if (ex instanceof ContaAzulHttpException httpEx && httpEx.isStatus(403) && isPlanIneligibleResponse(httpEx.getResponseBody())) {
                String message = "Conta Azul indisponÃƒÆ’Ã‚Â­vel para automaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o: conta sem elegibilidade de API (END_TRIAL).";
                log.warn(message);
                return new ReceiptProcessingResult(0, 0, 0, 0, 0, 0, 0, List.of(message));
            }
            log.error("Falha ao buscar vendas liquidadas no Conta Azul.", ex);
            return new ReceiptProcessingResult(0, 0, 0, 0, 1, 0, 0, List.of("Falha ao buscar vendas liquidadas no Conta Azul: " + ex.getMessage()));
        }

        int sent = 0;
        int skippedProcessed = 0;
        int skippedMapping = 0;
        int failures = 0;
        int noAttachmentWarnings = 0;
        int mappingWarnings = 0;
        List<String> errors = new ArrayList<>();

        for (ContaAzulClient.SaleItem sale : acquittedSales) {
            try {
                if (!applyThrottle()) {
                    continue;
                }

                auditService.logStart(sale.descricao(), sale.origem(), sale.parcelaId());

                String customerUuidFromParcel = validator.normalizeUuid(sale.customerUuid());
                String resolvedSaleId = resolveSaleIdForReceiptFlow(sale);
                String resolvedSaleNumber = resolveSaleNumberForReceiptFlow(sale, resolvedSaleId);
                String saleDescriptionForReceipt = resolveSaleDescriptionForReceiptFlow(sale, resolvedSaleNumber);

                Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
                if (baixaIdOpt.isEmpty()) {
                    auditService.recordNoBaixaFound(sale.parcelaId(), resolvedSaleId, errors);
                    continue;
                }

                String baixaId = baixaIdOpt.get().trim();
                if (!StringUtils.hasText(baixaId)) {
                    failures++;
                    auditService.recordInvalidBaixaId(sale.parcelaId(), errors);
                    continue;
                }

                if (concurrencyHandler.isAlreadyProcessed(baixaId)) {
                    skippedProcessed++;
                    log.info("Recibo/baixa {} jÃƒÆ’Ã‚Â¡ processado anteriormente. Ignorando.", baixaId);
                    continue;
                }

                if (!concurrencyHandler.acquireLock(baixaId)) {
                    skippedProcessed++;
                    log.info("Recibo/baixa {} jÃƒÆ’Ã‚Â¡ sendo processado em outra execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o simultÃƒÆ’Ã‚Â¢nea. Ignorando.", baixaId);
                    continue;
                }

                try {
                    ContaAzulReceiptValidator.ValidationResult validationResult = validator.validate(sale);
                    if (validationResult.isNotValid()) {
                        skippedMapping++;
                        mappingWarnings++;
                        log.warn("Recibo {} invÃƒÆ’Ã‚Â¡lido ou sem mapeamento: {}", baixaId, validationResult.errorMessage());
                        auditService.registerError(errors, validationResult.errorMessage());
                        continue;
                    }

                    DoctorEmailMapping mapping = validationResult.mapping();
                    String recipientEmail = validationResult.recipientEmail();
                    String doctorName = validator.resolveDoctorName(mapping, sale.customerName());

                    byte[] pdfBytes;
                    boolean usedInternalFallback = false;

                    if (!StringUtils.hasText(sale.idReciboDigital())) {
                        noAttachmentWarnings++;
                        try {
                            pdfBytes = fallbackService.generateInternalReceiptPdf(baixaId, doctorName, mapping, customerUuidFromParcel, saleDescriptionForReceipt);
                            usedInternalFallback = true;
                        } catch (RuntimeException fallbackEx) {
                            failures++;
                            auditService.recordFallbackFailure(baixaId, fallbackEx, errors);
                            continue;
                        }
                    } else {
                        try {
                            pdfBytes = storageAdapter.downloadReceiptPdf(baixaId);
                        } catch (NoReceiptAvailableException nr) {
                            noAttachmentWarnings++;
                            try {
                                pdfBytes = fallbackService.generateInternalReceiptPdf(baixaId, doctorName, mapping, customerUuidFromParcel, saleDescriptionForReceipt);
                                usedInternalFallback = true;
                            } catch (RuntimeException fallbackEx) {
                                failures++;
                                auditService.recordFallbackFailure(baixaId, fallbackEx, errors);
                                continue;
                            }
                        } catch (RuntimeException ex) {
                            failures++;
                            auditService.recordDownloadFailure(baixaId, resolvedSaleId, doctorName, ex, errors);
                            continue;
                        }
                    }

                    if (pdfBytes == null || pdfBytes.length == 0) {
                        failures++;
                        auditService.recordEmptyPdfFailure(baixaId, resolvedSaleId, doctorName, errors);
                        continue;
                    }

                    try {
                        receiptEmailService.sendReceiptForBaixa(doctorName, recipientEmail, resolvedSaleNumber, baixaId, pdfBytes);
                    } catch (RuntimeException emailEx) {
                        failures++;
                        auditService.recordEmailFailure(baixaId, resolvedSaleId, doctorName, emailEx, errors);
                        continue;
                    }

                    auditService.recordSuccess(baixaId, doctorName, usedInternalFallback, sale.customerName());
                    sent++;
                } finally {
                    concurrencyHandler.releaseLock(baixaId);
                }
            } catch (DataIntegrityViolationException ex) {
                skippedProcessed++;
                log.debug("Recibo jÃƒÆ’Ã‚Â¡ registrado como processado em execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o concorrente.", ex);
            } catch (RuntimeException ex) {
                failures++;
                log.error("Falha ao processar recibo.", ex);
                auditService.registerError(errors, "Falha ao processar recibo: " + ex.getMessage());
            }
        }

        log.info("AutomaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o ContaAzul finalizada: acquitted={}, sent={}, skippedProcessed={}, skippedMapping={}, failures={}",
                acquittedSales.size(), sent, skippedProcessed, skippedMapping, failures);

        return new ReceiptProcessingResult(acquittedSales.size(), sent, skippedProcessed, skippedMapping, failures, noAttachmentWarnings, mappingWarnings, List.copyOf(errors));
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
            log.warn("NÃƒÆ’Ã‚Â£o foi possÃƒÆ’Ã‚Â­vel resolver sale_id pela parcela {}. Mantendo fallback do item.", sale.parcelaId(), ex);
        }
        return fallbackSaleId;
    }

    private boolean isPlanIneligibleResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        String normalized = responseBody.toUpperCase();
        return normalized.contains("END_TRIAL") || normalized.contains("NAO ESTA ELEGIVEL");
    }

    private String resolveSaleNumberForReceiptFlow(ContaAzulClient.SaleItem sale, String resolvedSaleId) {
        if (StringUtils.hasText(sale.saleNumber()) && sale.saleNumber().trim().matches("\\d{3,}")) {
            return sale.saleNumber().trim();
        }
        String fromDescription = extractSaleNumber(sale.descricao());
        if (StringUtils.hasText(fromDescription)) {
            return fromDescription;
        }
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
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.matches("\\d{3,}")) {
            return normalized;
        }
        Matcher matcher = SALE_NUMBER_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group(1) : null;
    }

    public record ReceiptProcessingResult(
            int acquitted,
            int sent,
            int skippedProcessed,
            int skippedMapping,
            int failures,
            int noAttachmentWarnings,
            int mappingWarnings,
            List<String> errors) {

        static ReceiptProcessingResult empty() {
            return new ReceiptProcessingResult(0, 0, 0, 0, 0, 0, 0, List.of());
        }
    }

    private boolean applyThrottle() {
        LockSupport.parkNanos(350_000_000L);
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Thread interrompida durante throttling anti-429 da automaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o financeira.");
            return false;
        }
        return true;
    }

    private String buildSalesConfigurationErrorMessage() {
        List<String> missingProperties = new ArrayList<>();
        if (!StringUtils.hasText(properties.getSalesV2Url())) {
            missingProperties.add("app.contaazul.sales-v2-url");
        }
        if (!StringUtils.hasText(properties.getPaymentsUrl())) {
            missingProperties.add("app.contaazul.payments-url");
        }
        if (!StringUtils.hasText(properties.getSalesPdfV1UrlTemplate())) {
            missingProperties.add("app.contaazul.sales-pdf-v1-url-template");
        }
        String envSalesV2 = System.getenv("CONTAAZUL_SALES_V2_URL");
        String envSalesPdf = System.getenv("CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE");

        return "ConfiguraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o da Conta Azul incompleta. Propriedades vazias: "
                + (missingProperties.isEmpty() ? "nenhuma" : String.join(", ", missingProperties))
                + ". Estado atual -> app.contaazul.sales-v2-url="
                + (StringUtils.hasText(properties.getSalesV2Url()) ? "preenchida" : "vazia")
                + ", app.contaazul.payments-url="
                + (StringUtils.hasText(properties.getPaymentsUrl()) ? "preenchida" : "vazia")
                + ", app.contaazul.sales-pdf-v1-url-template="
                + (StringUtils.hasText(properties.getSalesPdfV1UrlTemplate()) ? "preenchida" : "vazia")
                + ", CONTAAZUL_SALES_V2_URL="
                + (StringUtils.hasText(envSalesV2) ? "preenchida" : "vazia")
                + ", CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE="
                + (StringUtils.hasText(envSalesPdf) ? "preenchida" : "vazia");
    }
}

