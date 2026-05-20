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
import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMappingRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
import com.fasterxml.jackson.databind.JsonNode;

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
    private static final int MAX_ERROR_DETAILS = 200;
    private static final String ROBERTO_TETSUO_UUID = "b68a0402-6620-4890-af48-909d8b38362b";
    private static final String DOCUMENT_FALLBACK_UNDER_REVIEW = "CPF/CNPJ sob consulta";
    private static final Pattern SALE_NUMBER_PATTERN = Pattern.compile("(?i)(?:numero_venda|numero|venda)\\s*[:#-]?\\s*(\\d{3,})");

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ReceiptEmailService receiptEmailService;
    private final InternalReceiptService internalReceiptService;
    private final ContaAzulProperties properties;

    private final ContaAzulReceiptStorageAdapter storageAdapter;
    private final ContaAzulReceiptRetryPolicy retryPolicy;

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
            throw new IllegalStateException("Token da Conta Azul ainda não autorizado para execução do teste real.");
        }

        ContaAzulClient.SaleItem sale = contaAzulClient.findAcquittedSaleById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venda não encontrada com status ACQUITTED: " + saleId));

        if (!StringUtils.hasText(sale.customerUuid())) {
            throw new IllegalStateException("A venda informada não possui customer UUID para localizar mapeamento.");
        }

        DoctorEmailMapping mapping = doctorEmailMappingRepository
                .findByContaAzulCustomerUuid(sale.customerUuid())
                .orElseThrow(() -> new IllegalStateException(
                        "Não existe mapeamento para o customer UUID informado: " + sale.customerUuid()));

        String recipientEmail = resolveRecipientEmail(mapping);
        if (!StringUtils.hasText(recipientEmail)) {
            throw new IllegalStateException("Mapeamento encontrado, mas sem e-mail de destino para envio.");
        }

        String doctorName = resolveDoctorName(mapping, sale.customerName());
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

        if (dataInicio == null || dataFim == null) {
            log.warn("Período inválido para sincronização: dataInicio={} dataFim={}", dataInicio, dataFim);
            return ReceiptProcessingResult.empty();
        }

        if (dataInicio.isAfter(dataFim)) {
            log.warn("Período inválido para sincronização: dataInicio ({}) maior que dataFim ({})", dataInicio, dataFim);
            return ReceiptProcessingResult.empty();
        }

        log.info("Automação ContaAzul: consultando endpoint financeiro de parcelas para mapear venda_id e baixar PDF por sale_id.");

        String dataVencimentoDe = dataInicio.format(DATE_FORMATTER);
        String dataVencimentoAte = dataFim.format(DATE_FORMATTER);

        log.info("Iniciando busca de parcelas recebidas no período: {} a {}", dataVencimentoDe, dataVencimentoAte);

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);
        } catch (RuntimeException ex) {
            if (ex instanceof ContaAzulHttpException httpEx
                    && httpEx.isStatus(403)
                    && isPlanIneligibleResponse(httpEx.getResponseBody())) {
                String message = "Conta Azul indisponível para automação: conta sem elegibilidade de API (END_TRIAL).";
                log.warn(message);
                return new ReceiptProcessingResult(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of(message));
            }

            log.error("Falha ao buscar vendas liquidadas no Conta Azul.", ex);
            return new ReceiptProcessingResult(
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    List.of("Falha ao buscar vendas liquidadas no Conta Azul: " + ex.getMessage()));
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

                log.info("[INICIO PROCESSAMENTO] Parcela: {}",
                        StringUtils.hasText(sale.descricao()) ? sale.descricao().trim() : "(sem descrição)");
                log.info("[ORIGEM] origem={} | parcelaId={}",
                        StringUtils.hasText(sale.origem()) ? sale.origem() : "(nula)",
                        StringUtils.hasText(sale.parcelaId()) ? sale.parcelaId() : "(sem id)");

                String customerUuidFromParcel = normalizeUuid(sale.customerUuid());
                log.info("Parcela recebida para processamento: parcelaId={}",
                        StringUtils.hasText(sale.parcelaId()) ? sale.parcelaId() : "(sem id)");

                String resolvedSaleId = resolveSaleIdForReceiptFlow(sale);
                String resolvedSaleNumber = resolveSaleNumberForReceiptFlow(sale, resolvedSaleId);
                String saleDescriptionForReceipt = resolveSaleDescriptionForReceiptFlow(sale, resolvedSaleNumber);

                Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
                if (baixaIdOpt.isEmpty()) {
                    log.error("Nenhuma baixa encontrada para a parcela {}. Marcando como processado para evitar loop.",
                            sale.parcelaId());
                    registerError(errors,
                        "Nenhuma baixa encontrada para a parcela " + sale.parcelaId() + ". Item não processado.");
                    String markId = StringUtils.hasText(sale.parcelaId())
                            ? sale.parcelaId()
                            : (StringUtils.hasText(resolvedSaleId) ? resolvedSaleId : null);

                    if (markId != null) {
                        retryPolicy.saveProcessedSaleIfNeeded(markId,
                                "Recibo {} registrado como processado (nenhuma baixa encontrada).",
                                "Recibo {} já registrado por concorrência ao marcar como processado quando nenhuma baixa encontrada.");
                    } else {
                        log.warn(
                                "Nenhum identificador disponível para marcar como processado (parcela sem id e sem saleId). Parcela descrição: {}",
                                sale.descricao());
                    }
                    continue;
                }

                String baixaId = baixaIdOpt.get().trim();
                if (!StringUtils.hasText(baixaId)) {
                    failures++;
                    log.warn("baixaId inválido (nulo/vazio) para parcela {}. Item ignorado para segurança.",
                            sale.parcelaId());
                    registerError(errors,
                        "baixaId inválido para parcela " + sale.parcelaId() + ". Item ignorado para segurança.");
                    continue;
                }

                log.info("Baixa ID extraído com sucesso para parcela {}: {}", sale.parcelaId(), baixaId);

                if (processedSaleRepository.existsBySaleId(baixaId)) {
                    skippedProcessed++;
                    log.info("Recibo/baixa {} já processado anteriormente. Ignorando.", baixaId);
                    continue;
                }

                if (!StringUtils.hasText(customerUuidFromParcel)) {
                    skippedMapping++;
                    mappingWarnings++;
                    log.warn("Recibo {} sem customer UUID. Pulando.", baixaId);
                    registerError(errors,
                            "Recibo " + baixaId + " sem customer UUID retornado pela API. Mapeamento ignorado.");
                    continue;
                }

                log.info("[FLOW] Baixa ID definido como: {}", baixaId);
                log.info("Verificando mapeamento para o médico...");

                DoctorEmailMapping mapping = findDoctorMappingByCustomerUuid(customerUuidFromParcel).orElse(null);
                if (mapping == null) {
                    skippedMapping++;
                    mappingWarnings++;
                    log.info("Mapeamento NÃO encontrado. Pulando item.");
                    log.warn("[MAP_FAIL] Cadastro faltando para o médico: {} | UUID API='{}' | UUID normalizado='{}'",
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "(nome indisponível)",
                        sale.customerUuid(),
                        customerUuidFromParcel);
                    if (ROBERTO_TETSUO_UUID.equals(customerUuidFromParcel)) {
                        log.warn(
                                "Dica: Verifique se o UUID b68a0402... está cadastrado na tabela doctor_email_mapping para o médico Roberto Tetsuo.");
                    }
                    registerError(errors,
                        "MAP_FAIL para UUID " + customerUuidFromParcel + " (parcela " + sale.parcelaId() + ").");
                    continue;
                }

                log.info("-> Médico no banco: {} (E-mail: {})", mapping.getDoctorName(), mapping.getDoctorEmail());

                String recipientEmail = resolveRecipientEmail(mapping);
                if (!StringUtils.hasText(recipientEmail)) {
                    skippedMapping++;
                    mappingWarnings++;
                    log.warn(
                            "Mapeamento sem e-mail de destino (user/fallback) para customer UUID {}. Recibo {} ignorado.",
                            customerUuidFromParcel,
                            baixaId);
                    registerError(errors,
                        "Mapeamento sem e-mail de destino para customer UUID " + customerUuidFromParcel
                            + " (recibo " + baixaId + ").");
                    continue;
                }

                String doctorName = resolveDoctorName(mapping, sale.customerName());
                log.info("Médico identificado para a parcela {}: {}. Prosseguindo para baixar PDF do Recibo da Baixa {}",
                        sale.parcelaId(),
                        doctorName,
                        baixaId);

                byte[] pdfBytes;
                boolean usedInternalFallback = false;

                if (!StringUtils.hasText(sale.idReciboDigital())) {
                    noAttachmentWarnings++;
                    log.info(
                            "Baixa {} sem id_recibo_digital no retorno da Conta Azul. Gerando recibo interno Inovare (fallback).",
                            baixaId);
                    try {
                        pdfBytes = generateInternalReceiptPdf(
                                baixaId,
                                doctorName,
                            mapping,
                            customerUuidFromParcel,
                            saleDescriptionForReceipt);
                        usedInternalFallback = true;
                    } catch (RuntimeException fallbackEx) {
                        failures++;
                        log.error("Falha ao gerar recibo interno para baixa {}.", baixaId, fallbackEx);
                        registerError(errors,
                                "Falha ao gerar recibo interno para baixa " + baixaId + ": " + fallbackEx.getMessage());
                        continue;
                    }
                } else {
                    try {
                        pdfBytes = storageAdapter.downloadReceiptPdf(baixaId);
                    } catch (NoReceiptAvailableException nr) {
                        noAttachmentWarnings++;
                        log.warn(
                                "Recibo digital da baixa {} indisponivel apos retries. Gerando recibo interno Inovare (fallback).",
                                baixaId);

                        try {
                            pdfBytes = generateInternalReceiptPdf(
                                    baixaId,
                                    doctorName,
                                    mapping,
                                    customerUuidFromParcel,
                                    saleDescriptionForReceipt);
                            usedInternalFallback = true;
                        } catch (RuntimeException fallbackEx) {
                            failures++;
                            log.error("Falha ao gerar recibo interno para baixa {} apos indisponibilidade de anexo.",
                                    baixaId,
                                    fallbackEx);
                            registerError(errors,
                                    "Falha no fallback interno da baixa " + baixaId + ": " + fallbackEx.getMessage());
                            continue;
                        }
                    } catch (RuntimeException ex) {
                        failures++;
                        String details = "Falha ao baixar recibo para baixa " + baixaId + ": " + ex.getMessage();
                        boolean failedPermanently = retryPolicy.registerAttemptAndCheckIfPermanentFailure(baixaId, resolvedSaleId, doctorName, details);
                        if (failedPermanently) {
                            log.error("Recibo da baixa {} falhou repetidamente. Marcado como processado e alerta registrado.", baixaId);
                        }
                        registerError(errors, details);
                        continue;
                    }
                }

                if (pdfBytes == null || pdfBytes.length == 0) {
                    failures++;
                    String details = "Recibo da baixa " + baixaId + " retornou PDF vazio. Tentará novamente.";
                    boolean failedPermanently = retryPolicy.registerAttemptAndCheckIfPermanentFailure(baixaId, resolvedSaleId, doctorName, details);
                    if (failedPermanently) {
                        log.error("Recibo da baixa {} não gerou bytes após limite de tentativas. Marcado como processado e alerta registrado.", baixaId);
                    }
                    registerError(errors, "Recibo da baixa " + baixaId + " retornou PDF vazio. Tentará novamente.");
                    continue;
                }

                retryPolicy.clearAttempts(baixaId);

                try {
                    receiptEmailService.sendReceiptForBaixa(
                            doctorName,
                            recipientEmail,
                            resolvedSaleNumber,
                            baixaId,
                            pdfBytes);
                } catch (RuntimeException emailEx) {
                    failures++;
                    String details = "Falha SMTP no envio do recibo da baixa " + baixaId
                            + ". O processamento da parcela foi preservado no banco para não perder trabalho. Erro: "
                            + emailEx.getMessage();
                    registerError(errors, details);
                    retryPolicy.handleEmailFailure(baixaId, resolvedSaleId, doctorName, details);
                    continue;
                }

                if (usedInternalFallback) {
                    log.info("Recibo interno enviado com sucesso para baixa {} (médico: {}).",
                        baixaId,
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");
                }

                log.info("E-mail enviado com sucesso para recibo {} (médico: {}).",
                        baixaId,
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

                retryPolicy.saveProcessedSaleIfNeeded(baixaId,
                    "Recibo {} registrado como processado com sucesso.",
                    "Recibo {} já registrado por concorrência ao finalizar processamento.");
                sent++;
            } catch (DataIntegrityViolationException ex) {
                skippedProcessed++;
                log.debug("Recibo {} já registrado como processado em execução concorrente.", ex.getMessage());
            } catch (RuntimeException ex) {
                failures++;
                log.error("Falha ao processar recibo.", ex);
                registerError(errors, "Falha ao processar recibo: " + ex.getMessage());
            }
        }

        log.info(
                "Automação ContaAzul finalizada: acquitted={}, sent={}, skippedProcessed={}, skippedMapping={}, failures={}",
                acquittedSales.size(),
                sent,
                skippedProcessed,
                skippedMapping,
                failures);

        return new ReceiptProcessingResult(
            acquittedSales.size(),
            sent,
            skippedProcessed,
            skippedMapping,
            failures,
            noAttachmentWarnings,
            mappingWarnings,
            List.copyOf(errors));
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

    private byte[] generateInternalReceiptPdf(
            String baixaId,
            String doctorName,
            DoctorEmailMapping mapping,
            String customerUuidFromSale,
            String saleDescription) {
        JsonNode settlementNode = null;

        try {
            settlementNode = contaAzulClient.getSettlementDetails(baixaId).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel obter detalhes da baixa {} para montagem completa do recibo interno.", baixaId, ex);
        }

        String documentForReceipt = resolveDoctorDocumentForReceipt(mapping, customerUuidFromSale, settlementNode, baixaId);

        byte[] pdfBytes = internalReceiptService.generateReceipt(
                settlementNode,
                doctorName,
                documentForReceipt,
                saleDescription);
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("Recibo interno gerado sem conteudo para baixa " + baixaId + ".");
        }

        return pdfBytes;
    }

    private String resolveDoctorDocumentForReceipt(
            DoctorEmailMapping mapping,
            String customerUuidFromSale,
            JsonNode settlementNode,
            String baixaId) {
        String mappedDocument = mapping != null ? mapping.getDoctorCpfCnpj() : null;
        if (mappedDocument != null) {
            mappedDocument = mappedDocument.trim();
        }
        if (mappedDocument != null && !mappedDocument.isBlank()) {
            return mappedDocument;
        }

        String doctorName = mapping != null ? mapping.getDoctorName() : "(médico não identificado)";
        log.warn("CPF/CNPJ nao encontrado no doctor_email_mapping para o medico {} (baixa {}).", doctorName, baixaId);

        if (StringUtils.hasText(customerUuidFromSale)) {
            Optional<String> apiDocument = contaAzulClient.fetchPersonDocumentById(customerUuidFromSale.trim());
            if (apiDocument.isPresent()) {
                String resolvedDocument = apiDocument.get().trim();
                persistDoctorDocumentOnMapping(mapping, resolvedDocument, customerUuidFromSale, baixaId);
                log.info("Documento obtido via API da pessoa {} para baixa {}.", customerUuidFromSale, baixaId);
                return resolvedDocument;
            }
            log.warn("Nao foi possivel obter documento via API para cliente_id da venda {} (baixa {}).",
                    customerUuidFromSale,
                    baixaId);
        }

        String clientIdFromSettlement = extractClientIdFromSettlement(settlementNode);
        if (StringUtils.hasText(clientIdFromSettlement)) {
            Optional<String> apiDocument = contaAzulClient.fetchPersonDocumentById(clientIdFromSettlement);
            if (apiDocument.isPresent()) {
                String resolvedDocument = apiDocument.get().trim();
                persistDoctorDocumentOnMapping(mapping, resolvedDocument, clientIdFromSettlement, baixaId);
                log.info("Documento obtido via API da pessoa {} para baixa {}.", clientIdFromSettlement, baixaId);
                return resolvedDocument;
            }

            log.warn("Nao foi possivel obter documento via API para cliente_id {} (baixa {}). Usando fallback.",
                    clientIdFromSettlement,
                    baixaId);
        } else {
            log.warn("cliente_id nao encontrado no JSON da baixa {}. Mantendo documento em fallback interno.", baixaId);
        }

        return DOCUMENT_FALLBACK_UNDER_REVIEW;
    }

    private void persistDoctorDocumentOnMapping(
            DoctorEmailMapping mapping,
            String doctorDocument,
            String clientId,
            String baixaId) {
        if (mapping == null || !StringUtils.hasText(doctorDocument)) {
            return;
        }

        if (StringUtils.hasText(mapping.getDoctorCpfCnpj())
                && doctorDocument.trim().equalsIgnoreCase(mapping.getDoctorCpfCnpj().trim())) {
            return;
        }

        try {
            mapping.setDoctorCpfCnpj(doctorDocument.trim());
            doctorEmailMappingRepository.save(mapping);
            log.info("CPF/CNPJ atualizado no doctor_email_mapping via API da pessoa {} (baixa {}).", clientId, baixaId);
        } catch (RuntimeException ex) {
            log.warn("Falha ao persistir CPF/CNPJ no doctor_email_mapping para cliente_id {}.", clientId, ex);
        }
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
        if (StringUtils.hasText(fromResolvedSaleId)) {
            return fromResolvedSaleId;
        }

        return "N/D";
    }

    private String resolveSaleDescriptionForReceiptFlow(ContaAzulClient.SaleItem sale, String resolvedSaleNumber) {
        if (StringUtils.hasText(resolvedSaleNumber) && !"N/D".equalsIgnoreCase(resolvedSaleNumber)) {
            return "Venda " + resolvedSaleNumber;
        }

        if (StringUtils.hasText(sale.descricao())) {
            return sale.descricao().trim();
        }

        return "Venda N/D";
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

    private String extractClientIdFromSettlement(JsonNode settlementNode) {
        if (settlementNode == null || settlementNode.isNull()) {
            return null;
        }

        String clientId = null;
        if (settlementNode.path("cliente_id").isValueNode()) {
            clientId = settlementNode.path("cliente_id").asText();
        }

        if (!StringUtils.hasText(clientId) && settlementNode.path("cliente").path("id").isValueNode()) {
            clientId = settlementNode.path("cliente").path("id").asText();
        }

        if (!StringUtils.hasText(clientId) && settlementNode.path("customer").path("id").isValueNode()) {
            clientId = settlementNode.path("customer").path("id").asText();
        }

        if (!StringUtils.hasText(clientId) && settlementNode.path("pessoa").path("id").isValueNode()) {
            clientId = settlementNode.path("pessoa").path("id").asText();
        }

        if (clientId == null || clientId.isBlank()) {
            return null;
        }

        return clientId.trim();
    }

    private String resolveRecipientEmail(DoctorEmailMapping mapping) {
        if (mapping.getUser() != null && StringUtils.hasText(mapping.getUser().getEmail())) {
            return mapping.getUser().getEmail();
        }

        return mapping.getDoctorEmail();
    }

    private String resolveDoctorName(DoctorEmailMapping mapping, String customerName) {
        if (mapping.getUser() != null && StringUtils.hasText(mapping.getUser().getName())) {
            return mapping.getUser().getName();
        }

        if (StringUtils.hasText(mapping.getDoctorName())) {
            return mapping.getDoctorName();
        }

        return StringUtils.hasText(customerName) ? customerName : "Profissional";
    }

    private Optional<DoctorEmailMapping> findDoctorMappingByCustomerUuid(String customerUuidFromParcel) {
        if (!StringUtils.hasText(customerUuidFromParcel)) {
            return Optional.empty();
        }

        String normalizedParcelCustomerUuid = normalizeUuid(customerUuidFromParcel);

        Optional<DoctorEmailMapping> normalizedMatch = doctorEmailMappingRepository
                .findByContaAzulCustomerUuidNormalized(normalizedParcelCustomerUuid);
        if (normalizedMatch.isPresent()) {
            return normalizedMatch;
        }

        Optional<DoctorEmailMapping> direct = doctorEmailMappingRepository
                .findByContaAzulCustomerUuid(normalizedParcelCustomerUuid);
        if (direct.isPresent()) {
            return direct;
        }

        return doctorEmailMappingRepository.findAllByOrderByDoctorNameAsc().stream()
                .filter(mapping -> StringUtils.hasText(mapping.getContaAzulCustomerUuid()))
                .filter(mapping -> normalizedParcelCustomerUuid.equals(normalizeUuid(mapping.getContaAzulCustomerUuid())))
                .findFirst();
    }

    private String normalizeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.replaceAll("\\s+", "").toLowerCase();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private void registerError(List<String> errors, String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }

        if (errors.size() >= MAX_ERROR_DETAILS) {
            return;
        }

        errors.add(message.trim());
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