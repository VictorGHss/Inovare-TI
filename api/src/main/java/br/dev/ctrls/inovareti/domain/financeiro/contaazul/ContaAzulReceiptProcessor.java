package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMapping;
import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMappingRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSale;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessingAttempt;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessingAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulReceiptProcessor {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int SETTLEMENT_DETAILS_MAX_ATTEMPTS = 2;
    private static final long SETTLEMENT_DETAILS_RETRY_WAIT_NANOS = 15_000_000_000L;

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
    private final ReceiptEmailService receiptEmailService;
    private final ReceiptAlertService receiptAlertService;
    private final FinancialResponseMapper financialResponseMapper;

    @Value("${app.contaazul.automation.enabled:true}")
    private boolean automationEnabled;

    @Value("${app.contaazul.sales-v2-url}")
    private String salesV2Url;

    @Value("${app.contaazul.payments-url}")
    private String receivableEventsSearchUrl;

    @Value("${app.contaazul.sales-pdf-v1-url-template}")
    private String salesPdfV1UrlTemplate;

    @Value("${app.contaazul.api-v2-base-url:https://api-v2.contaazul.com}")
    private String contaAzulApiV2BaseUrl;

    @PostConstruct
    public void logContaAzulAutomationConfigOnBoot() {
        String envSalesV2 = System.getenv("CONTAAZUL_SALES_V2_URL");
        String envSalesPdf = System.getenv("CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE");

        log.info(
                "Diagnóstico Conta Azul no boot: app.contaazul.api-v2-base-url={}, app.contaazul.payments-url={}, app.contaazul.sales-v2-url={}, app.contaazul.sales-pdf-v1-url-template={}, CONTAAZUL_SALES_V2_URL={}, CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE={}",
                contaAzulApiV2BaseUrl,
                receivableEventsSearchUrl,
                StringUtils.hasText(salesV2Url) ? "preenchida" : "vazia",
                StringUtils.hasText(salesPdfV1UrlTemplate) ? "preenchida" : "vazia",
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

        String baixaId = baixaIdOpt.get().trim();
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalStateException("baixaId inválido (nulo/vazio) para a parcela da venda informada.");
        }

        log.info("Baixa ID extraído com sucesso para parcela {}: {}", sale.parcelaId(), baixaId);

        byte[] pdfBytes = downloadReceiptPdfFromSettlement(baixaId);
        log.info("PDF baixado ({} bytes) para a venda {}.", pdfBytes.length, sale.saleId());

        log.info("Enviando para {}", recipientEmail);
        receiptEmailService.sendReceiptForRealSaleTest(
                doctorName,
                recipientEmail,
                StringUtils.hasText(sale.saleNumber()) ? sale.saleNumber() : "N/D",
                sale.saleId(),
                pdfBytes);

        log.info("Teste real finalizado com sucesso para a venda {}.", sale.saleId());
        return new TesteEnvioRealResult(sale.saleId(), doctorName, recipientEmail, pdfBytes.length);
    }

    public void processCurrentMonthAcquittedSales() {
        LocalDate hoje = LocalDate.now();
        LocalDate primeiroDiaMesAtual = hoje.withDayOfMonth(1);
        processAcquittedSales(primeiroDiaMesAtual, hoje);
    }

    public void processAcquittedSales(LocalDate dataInicio, LocalDate dataFim) {
        if (!automationEnabled) {
            log.info("Pooling Conta Azul desativado por configuração.");
            return;
        }

        if (!contaAzulClient.hasSalesConfiguration()) {
            log.warn("Automação ContaAzul desabilitada: {}", buildSalesConfigurationErrorMessage());
            return;
        }

        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.debug("Automação ContaAzul: token ainda não autorizado. Pulando execução.");
            return;
        }

        if (dataInicio == null || dataFim == null) {
            log.warn("Período inválido para sincronização: dataInicio={} dataFim={}", dataInicio, dataFim);
            return;
        }

        if (dataInicio.isAfter(dataFim)) {
            log.warn("Período inválido para sincronização: dataInicio ({}) maior que dataFim ({})", dataInicio, dataFim);
            return;
        }

        log.info("Automação ContaAzul: consultando endpoint financeiro de parcelas para mapear venda_id e baixar PDF por sale_id.");

        String dataVencimentoDe = dataInicio.format(DATE_FORMATTER);
        String dataVencimentoAte = dataFim.format(DATE_FORMATTER);

        log.info("Iniciando busca de parcelas recebidas no período: {} a {}", dataVencimentoDe, dataVencimentoAte);

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);
        } catch (RuntimeException ex) {
            log.error("Falha ao buscar vendas liquidadas no Conta Azul.", ex);
            return;
        }

        int sent = 0;
        int skippedProcessed = 0;
        int skippedMapping = 0;
        int failures = 0;

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

                Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
                if (baixaIdOpt.isEmpty()) {
                    log.error("Nenhuma baixa encontrada para a parcela {}. Marcando como processado para evitar loop.",
                            sale.parcelaId());
                    String markId = StringUtils.hasText(sale.parcelaId())
                            ? sale.parcelaId()
                            : (StringUtils.hasText(sale.saleId()) ? sale.saleId() : null);

                    if (markId != null) {
                        try {
                            processedSaleRepository.save(ProcessedSale.builder().saleId(markId).build());
                            log.info("Recibo {} registrado como processado (nenhuma baixa encontrada).", markId);
                        } catch (DataIntegrityViolationException ex) {
                            log.debug(
                                    "Recibo {} já registrado por concorrência ao marcar como processado quando nenhuma baixa encontrada.",
                                    markId);
                        }
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
                    log.warn("Recibo {} sem customer UUID. Pulando.", baixaId);
                    continue;
                }

                log.info("[FLOW] Baixa ID definido como: {}", baixaId);
                log.info("Verificando mapeamento para o médico...");

                DoctorEmailMapping mapping = findDoctorMappingByCustomerUuid(customerUuidFromParcel).orElse(null);
                if (mapping == null) {
                    skippedMapping++;
                    log.info("Mapeamento NÃO encontrado. Pulando item.");
                    log.warn("[MAP_FAIL] Cadastro faltando para o médico: {}",
                            StringUtils.hasText(sale.customerName()) ? sale.customerName() : "(nome indisponível)");
                    continue;
                }

                log.info("-> Médico no banco: {} (E-mail: {})", mapping.getDoctorName(), mapping.getDoctorEmail());

                String recipientEmail = resolveRecipientEmail(mapping);
                if (!StringUtils.hasText(recipientEmail)) {
                    skippedMapping++;
                    log.warn(
                            "Mapeamento sem e-mail de destino (user/fallback) para customer UUID {}. Recibo {} ignorado.",
                            customerUuidFromParcel,
                            baixaId);
                    continue;
                }

                String doctorName = resolveDoctorName(mapping, sale.customerName());
                log.info("Médico identificado para a parcela {}: {}. Prosseguindo para baixar PDF do Recibo da Baixa {}",
                        sale.parcelaId(),
                        doctorName,
                        baixaId);

                byte[] pdfBytes;
                try {
                    pdfBytes = downloadReceiptPdfFromSettlement(baixaId);
                } catch (NoReceiptAvailableException nr) {
                    ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
                    if (attempt == null) {
                        processingAttemptRepository.save(ProcessingAttempt.builder().saleId(baixaId).attempts(1).build());
                        log.info("Recibo ainda não gerado pelo ERP para a baixa {}. Tentando novamente na próxima execução.",
                                baixaId);
                    } else {
                        int attempts = attempt.getAttempts() + 1;
                        attempt.setAttempts(attempts);
                        processingAttemptRepository.save(attempt);
                        if (attempts >= 20) {
                            try {
                                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                                processingAttemptRepository.deleteBySaleId(baixaId);
                                String details = "Recibo da baixa " + baixaId + " não gerou PDF após " + attempts
                                        + " tentativas. Marcado como processado para evitar loop.";
                                receiptAlertService.notifyPermanentReceiptFailure(
                                    baixaId,
                                    sale.saleId(),
                                    mapping.getDoctorName(),
                                    attempts,
                                    details);
                                log.error(
                                        "Recibo da baixa {} não gerado após {} tentativas. Marcado como processado e alerta registrado.",
                                        baixaId,
                                        attempts);
                            } catch (DataIntegrityViolationException dex) {
                                log.debug(
                                        "Recibo {} já registrado por concorrência ao marcar como processado após tentativas.",
                                        baixaId);
                            }
                        } else {
                            log.info(
                                    "Recibo ainda não gerado pelo ERP para a baixa {}. Tentando novamente na próxima execução. Tentativa {}/20",
                                    baixaId,
                                    attempts);
                        }
                    }
                    continue;
                } catch (RuntimeException ex) {
                    ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
                    if (attempt == null) {
                        processingAttemptRepository.save(ProcessingAttempt.builder().saleId(baixaId).attempts(1).build());
                    } else {
                        int attempts = attempt.getAttempts() + 1;
                        attempt.setAttempts(attempts);
                        processingAttemptRepository.save(attempt);
                        if (attempts >= 20) {
                            try {
                                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                                processingAttemptRepository.deleteBySaleId(baixaId);
                                String details = "Falha ao baixar recibo da baixa " + baixaId + " após " + attempts
                                        + " tentativas. Erro: " + ex.getMessage();
                                receiptAlertService.notifyPermanentReceiptFailure(
                                    baixaId,
                                    sale.saleId(),
                                    mapping.getDoctorName(),
                                    attempts,
                                    details);
                                log.error(
                                        "Recibo da baixa {} falhou repetidamente ({} tentativas). Marcado como processado e alerta registrado.",
                                        baixaId,
                                        attempts);
                            } catch (DataIntegrityViolationException dex) {
                                log.debug("Recibo {} já registrado por concorrência ao marcar como processado após falhas.",
                                        baixaId);
                            }
                        }
                    }
                    failures++;
                    log.error("Falha ao baixar recibo para baixa {}.", baixaId, ex);
                    continue;
                }

                if (pdfBytes == null || pdfBytes.length == 0) {
                    ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
                    if (attempt == null) {
                        processingAttemptRepository.save(ProcessingAttempt.builder().saleId(baixaId).attempts(1).build());
                    } else {
                        int attempts = attempt.getAttempts() + 1;
                        attempt.setAttempts(attempts);
                        processingAttemptRepository.save(attempt);
                        if (attempts >= 20) {
                            try {
                                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                                processingAttemptRepository.deleteBySaleId(baixaId);
                                String details = "Recibo da baixa " + baixaId + " não gerou bytes após " + attempts
                                        + " tentativas. Marcado como processado.";
                                receiptAlertService.notifyPermanentReceiptFailure(
                                    baixaId,
                                    sale.saleId(),
                                    mapping.getDoctorName(),
                                    attempts,
                                    details);
                                log.error(
                                        "Recibo da baixa {} não gerou bytes após {} tentativas. Marcado como processado e alerta registrado.",
                                        baixaId,
                                        attempts);
                            } catch (DataIntegrityViolationException dex) {
                                log.debug(
                                        "Recibo {} já registrado por concorrência ao marcar como processado após tentativas.",
                                        baixaId);
                            }
                        } else {
                            log.info(
                                    "Recibo ainda não gerado pelo ERP para a baixa {}. Tentando novamente na próxima execução. Tentativa {}/20",
                                    baixaId,
                                    attempt.getAttempts());
                        }
                    }
                    continue;
                }

                processingAttemptRepository.deleteBySaleId(baixaId);

                receiptEmailService.sendReceiptForBaixa(
                        doctorName,
                        recipientEmail,
                        StringUtils.hasText(sale.saleNumber()) ? sale.saleNumber() : "N/D",
                        baixaId,
                        pdfBytes);

                log.info("E-mail enviado com sucesso para recibo {} (médico: {}).",
                        baixaId,
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                log.info("Recibo {} registrado como processado com sucesso.", baixaId);
                sent++;
            } catch (DataIntegrityViolationException ex) {
                skippedProcessed++;
                log.debug("Recibo {} já registrado como processado em execução concorrente.", ex.getMessage());
            } catch (RuntimeException ex) {
                failures++;
                log.error("Falha ao processar recibo.", ex);
            }
        }

        log.info(
                "Automação ContaAzul finalizada: acquitted={}, sent={}, skippedProcessed={}, skippedMapping={}, failures={}",
                acquittedSales.size(),
                sent,
                skippedProcessed,
                skippedMapping,
                failures);
    }

    /**
     * Resolve URL do recibo a partir dos anexos da baixa e baixa o PDF autenticado.
     */
    private byte[] downloadReceiptPdfFromSettlement(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalArgumentException("baixaId não pode ser nulo/vazio para baixar recibo.");
        }

        String normalizedBaixaId = baixaId.trim();
        String receiptUrl = resolveReceiptUrlFromSettlementWithRetry(normalizedBaixaId)
                .orElseThrow(() -> new NoReceiptAvailableException(
                        "Nenhum anexo de recibo encontrado para baixa " + normalizedBaixaId));

        String accessToken = contaAzulTokenService.getValidAccessToken();
        return receiptEmailService.downloadReceiptBinary(receiptUrl, accessToken);
    }

    /**
     * Busca detalhes da baixa para extrair URL do recibo.
     *
     * Regra: quando o array de anexos vier vazio, aguarda 15 segundos e tenta
     * mais uma vez (máximo 2 tentativas).
     */
    private Optional<String> resolveReceiptUrlFromSettlementWithRetry(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            log.warn("resolveReceiptUrlFromSettlementWithRetry chamado com baixaId vazio.");
            return Optional.empty();
        }

        String normalizedBaixaId = baixaId.trim();

        for (int attempt = 1; attempt <= SETTLEMENT_DETAILS_MAX_ATTEMPTS; attempt++) {
            Optional<JsonNode> settlementNodeOpt = contaAzulClient.getSettlementDetails(normalizedBaixaId);
            if (settlementNodeOpt.isEmpty()) {
                return Optional.empty();
            }

            JsonNode settlementNode = settlementNodeOpt.get();
            Optional<String> receiptUrlOpt = financialResponseMapper.extractReceiptUrl(settlementNode);
            if (receiptUrlOpt.isPresent()) {
                return receiptUrlOpt;
            }

            boolean attachmentsEmpty = financialResponseMapper.isSettlementAttachmentsEmpty(settlementNode);
            if (!attachmentsEmpty || attempt >= SETTLEMENT_DETAILS_MAX_ATTEMPTS) {
                return Optional.empty();
            }

            log.info(
                    "Anexos ainda vazios para baixa {} na tentativa {}/{}. Aguardando 15 segundos para nova consulta.",
                    normalizedBaixaId,
                    attempt,
                    SETTLEMENT_DETAILS_MAX_ATTEMPTS);
            waitBeforeSettlementRetry();
            if (Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private void waitBeforeSettlementRetry() {
        LockSupport.parkNanos(SETTLEMENT_DETAILS_RETRY_WAIT_NANOS);
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrompida durante espera de retry para anexos da baixa.");
        }
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
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
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

        if (!StringUtils.hasText(salesV2Url)) {
            missingProperties.add("app.contaazul.sales-v2-url");
        }

        if (!StringUtils.hasText(receivableEventsSearchUrl)) {
            missingProperties.add("app.contaazul.payments-url");
        }

        if (!StringUtils.hasText(salesPdfV1UrlTemplate)) {
            missingProperties.add("app.contaazul.sales-pdf-v1-url-template");
        }

        String envSalesV2 = System.getenv("CONTAAZUL_SALES_V2_URL");
        String envSalesPdf = System.getenv("CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE");

        return "Configuração da Conta Azul incompleta. Propriedades vazias: "
                + (missingProperties.isEmpty() ? "nenhuma" : String.join(", ", missingProperties))
                + ". Estado atual -> app.contaazul.sales-v2-url="
                + (StringUtils.hasText(salesV2Url) ? "preenchida" : "vazia")
                + ", app.contaazul.payments-url="
                + (StringUtils.hasText(receivableEventsSearchUrl) ? "preenchida" : "vazia")
                + ", app.contaazul.sales-pdf-v1-url-template="
                + (StringUtils.hasText(salesPdfV1UrlTemplate) ? "preenchida" : "vazia")
                + ", CONTAAZUL_SALES_V2_URL="
                + (StringUtils.hasText(envSalesV2) ? "preenchida" : "vazia")
                + ", CONTAAZUL_SALE_PDF_V1_URL_TEMPLATE="
                + (StringUtils.hasText(envSalesPdf) ? "preenchida" : "vazia");
    }
}