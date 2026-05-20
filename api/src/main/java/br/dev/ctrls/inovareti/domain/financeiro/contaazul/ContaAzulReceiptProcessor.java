package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

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

import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMapping;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço que atua como orquestrador do fluxo da camada de aplicação (Application Service)
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
                "Diagnóstico Conta Azul no boot: app.contaazul.api-v2-base-url={}, app.contaazul.payments-url={}, app.contaazul.sales-v2-url={}, app.contaazul.sales-pdf-v1-url-template={}, CONTAAZUL_SALES_V2_URL={}, CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE={}",
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
            throw new IllegalStateException("Token da Conta Azul ainda não autorizado para execução del teste real.");
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

        Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
        if (baixaIdOpt.isEmpty()) {
            throw new IllegalStateException("Nenhuma baixa encontrada para a parcela da venda informada.");
        }

        String resolvedSaleId = resolveSaleIdForReceiptFlow(sale);
        String resolvedSaleNumber = resolveSaleNumberForReceiptFlow(sale, resolvedSaleId);

        String baixaId = baixaIdOpt.get().trim();
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalStateException("baixaId inválido (nulo/vazio) para a parcela da venda informada.");
        }

        log.info("Baixa ID extraído com sucesso para parcela {}: {}", sale.parcelaId(), baixaId);

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
            log.info("Pooling Conta Azul desativado por configuração.");
            return ReceiptProcessingResult.empty();
        }

        if (!contaAzulClient.hasSalesConfiguration()) {
            log.warn("Automação ContaAzul desabilitada: {}", buildSalesConfigurationErrorMessage());
            return ReceiptProcessingResult.empty();
        }

        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.debug("Automação ContaAzul: token ainda não autorizado. Pulando execução.");
            return ReceiptProcessingResult.empty();
        }

        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)) {
            log.warn("Período inválido para sincronização: dataInicio={} dataFim={}", dataInicio, dataFim);
            return ReceiptProcessingResult.empty();
        }

        log.info("Automação ContaAzul: consultando endpoint de parcelas recebidas no período: {} a {}", dataInicio, dataFim);

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales(dataInicio.format(DATE_FORMATTER), dataFim.format(DATE_FORMATTER));
        } catch (RuntimeException ex) {
            if (ex instanceof ContaAzulHttpException httpEx && httpEx.isStatus(403) && isPlanIneligibleResponse(httpEx.getResponseBody())) {
                String message = "Conta Azul indisponível para automação: conta sem elegibilidade de API (END_TRIAL).";
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
                    log.info("Recibo/baixa {} já processado anteriormente. Ignorando.", baixaId);
                    continue;
                }

                if (!concurrencyHandler.acquireLock(baixaId)) {
                    skippedProcessed++;
                    log.info("Recibo/baixa {} já sendo processado em outra execução simultânea. Ignorando.", baixaId);
                    continue;
                }

                try {
                    ContaAzulReceiptValidator.ValidationResult validationResult = validator.validate(sale);
                    if (validationResult.isNotValid()) {
                        skippedMapping++;
                        mappingWarnings++;
                        log.warn("Recibo {} inválido ou sem mapeamento: {}", baixaId, validationResult.errorMessage());
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
                log.debug("Recibo já registrado como processado em execução concorrente.", ex);
            } catch (RuntimeException ex) {
                failures++;
                log.error("Falha ao processar recibo.", ex);
                auditService.registerError(errors, "Falha ao processar recibo: " + ex.getMessage());
            }
        }

        log.info("Automação ContaAzul finalizada: acquitted={}, sent={}, skippedProcessed={}, skippedMapping={}, failures={}",
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
            log.warn("Não foi possível resolver sale_id pela parcela {}. Mantendo fallback do item.", sale.parcelaId(), ex);
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
            log.warn("Thread interrompida durante throttling anti-429 da automação financeira.");
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

        return "Configuração da Conta Azul incompleta. Propriedades vazias: "
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