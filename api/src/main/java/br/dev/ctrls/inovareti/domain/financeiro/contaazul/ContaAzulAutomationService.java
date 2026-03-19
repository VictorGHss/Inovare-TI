package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMapping;
import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMappingRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSale;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
import br.dev.ctrls.inovareti.domain.notification.FinanceEmailService;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulAutomationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String EDUARDO_BISINELLA_CUSTOMER_ID = "a4d63616-f502-4232-91a6-5c7e07e467b8";
    private static final Pattern SALE_NUMBER_FROM_DESCRIPTION_PATTERN = Pattern.compile("^Venda\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;
    private final ProcessedSaleRepository processedSaleRepository;
    private final FinanceEmailService financeEmailService;
    private final UserRepository userRepository;

    public SyncDoctorsResult syncAllDoctorsFromContaAzul() {
        List<ContaAzulClient.PessoaItem> pessoas = contaAzulClient.fetchAllPessoas();

        int created = 0;
        int updated = 0;

        for (ContaAzulClient.PessoaItem pessoa : pessoas) {
            if (!StringUtils.hasText(pessoa.id()) || !StringUtils.hasText(pessoa.email())) {
                continue;
            }

            String customerUuid = pessoa.id().trim();
            String doctorEmail = pessoa.email().trim();
            String doctorName = StringUtils.hasText(pessoa.nome()) ? pessoa.nome().trim() : null;

            User matchedUser = userRepository.findByEmail(doctorEmail).orElse(null);

            DoctorEmailMapping mapping = doctorEmailMappingRepository
                    .findByContaAzulCustomerUuid(customerUuid)
                    .orElse(null);

            if (mapping == null) {
                DoctorEmailMapping newMapping = DoctorEmailMapping.builder()
                        .contaAzulCustomerUuid(customerUuid)
                        .doctorName(doctorName)
                        .doctorEmail(doctorEmail)
                        .user(matchedUser)
                        .build();

                doctorEmailMappingRepository.save(newMapping);
                created++;
                continue;
            }

            mapping.setDoctorName(doctorName);
            mapping.setDoctorEmail(doctorEmail);

            if (matchedUser != null) {
                mapping.setUser(matchedUser);
            }

            doctorEmailMappingRepository.save(mapping);
            updated++;
        }

        return new SyncDoctorsResult(created, updated);
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
        byte[] pdfBytes = contaAzulClient.downloadSalePdf(sale.saleId());
        log.info("PDF baixado ({} bytes) para a venda {}.", pdfBytes.length, sale.saleId());

        log.info("Enviando para {}", recipientEmail);
        financeEmailService.sendReceiptEmailWithPdf(
                doctorName,
                recipientEmail,
                buildEmailBody(sale, doctorName),
                pdfBytes,
                "recibo-venda-" + sale.saleId() + ".pdf");

        log.info("Teste real finalizado com sucesso para a venda {}.", sale.saleId());
        return new TesteEnvioRealResult(sale.saleId(), doctorName, recipientEmail, pdfBytes.length);
    }

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

        /**
         * Orquestra o fluxo de automação de vendas liquidadas:
         * consulta, valida mapeamento do médico, envia e-mail e marca idempotência.
         */
        @Scheduled(
            fixedDelayString = "${app.contaazul.automation.fixed-delay-ms:300000}",
            initialDelayString = "${app.contaazul.automation.initial-delay-ms:180000}")
    public void processAcquittedSales() {
        log.info("Iniciando pooling de vendas liquidadas na Conta Azul.");

        if (!automationEnabled) {
            log.info("Pooling Conta Azul desativado por configuração.");
            return;
        }

        if (!contaAzulClient.hasSalesConfiguration()) {
            log.warn("Automação ContaAzul desabilitada: {}", buildSalesConfigurationErrorMessage());
            return;
        }

        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.info("Automação ContaAzul: token ainda não autorizado. Pulando execução.");
            return;
        }

        log.info("Automação ContaAzul: consultando endpoint financeiro de parcelas para mapear venda_id e baixar PDF por sale_id.");

        LocalDate hoje = LocalDate.now();
        LocalDate primeiroDiaMesAtual = hoje.withDayOfMonth(1);
        String dataVencimentoDe = primeiroDiaMesAtual.format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);

        log.info("Iniciando busca de parcelas recebidas no período: {} a {}", dataVencimentoDe, dataVencimentoAte);

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);

            boolean hasVendaMappedFromFinancial = acquittedSales.stream()
                    .anyMatch(item -> item.venda() != null && StringUtils.hasText(item.venda().id()));

            if (!hasVendaMappedFromFinancial) {
                log.warn("Endpoint financeiro não retornou venda.id nas parcelas. Aplicando fallback via /v1/sales (COMMITTED).");
                acquittedSales = contaAzulClient.fetchCommittedSalesWithAcquittedParcels();
            }
        } catch (RuntimeException ex) {
            log.error("Falha ao buscar vendas liquidadas no Conta Azul.", ex);
            return;
        }

        log.info("Pooling Conta Azul: {} venda(s) mapeadas a partir de parcelas recebidas.", acquittedSales.size());

        int sent = 0;
        int skippedProcessed = 0;
        int skippedMapping = 0;
        int failures = 0;
        Map<String, String> saleNumberToUuid = new HashMap<>();

        for (ContaAzulClient.SaleItem sale : acquittedSales) {
            try {
                if (!"VENDA".equalsIgnoreCase(sale.origem())) {
                    continue;
                }

                String saleIdToProcess = sale.venda() != null && StringUtils.hasText(sale.venda().id())
                        ? sale.venda().id()
                        : null;

                if (!StringUtils.hasText(saleIdToProcess)) {
                    String saleNumberFromDescription = extractSaleNumberFromDescription(sale.descricao());

                    if (StringUtils.hasText(saleNumberFromDescription)) {
                        log.debug("Tentando encontrar UUID da venda para o número {} extraído da descrição.", saleNumberFromDescription);

                        if (saleNumberToUuid.isEmpty()) {
                            List<ContaAzulClient.SaleItem> committedSales = contaAzulClient.fetchCommittedSalesWithAcquittedParcels();
                            saleNumberToUuid = buildSaleNumberToUuidIndex(committedSales);
                        }

                        saleIdToProcess = saleNumberToUuid.get(saleNumberFromDescription);
                    }
                }

                if (!StringUtils.hasText(saleIdToProcess)) {
                    log.warn("Atenção: Parcela {} tem origem VENDA mas o objeto venda está nulo", sale.parcelaId());
                    continue;
                }

                log.debug("Parcela {} vinculada à Venda {} identificada com sucesso.", sale.parcelaId(), saleIdToProcess);

                log.debug(
                        "Analisando parcela {}. Venda vinculada: {}. Cliente: {}",
                        sale.parcelaId(),
                        saleIdToProcess,
                        sale.customerUuid());

                log.info("Processando venda {} do médico {}.",
                        saleIdToProcess,
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

                if (processedSaleRepository.existsBySaleId(saleIdToProcess)) {
                    skippedProcessed++;
                    log.info("Venda {} já processada anteriormente. Ignorando.", saleIdToProcess);
                    continue;
                }

                if (!StringUtils.hasText(sale.customerUuid())) {
                    skippedMapping++;
                    log.warn("Venda {} sem customer UUID. Pulando.", saleIdToProcess);
                    continue;
                }

                log.trace(
                    "Verificando parcela ID {}. Cliente ID: {}. Existe mapeamento para este médico?",
                    sale.parcelaId(),
                    sale.customerUuid());

                DoctorEmailMapping mapping = doctorEmailMappingRepository
                        .findByContaAzulCustomerUuid(sale.customerUuid())
                        .orElse(null);

                if (mapping == null) {
                    skippedMapping++;
                    if (EDUARDO_BISINELLA_CUSTOMER_ID.equals(sale.customerUuid())) {
                        log.warn("Médico não encontrado na tabela doctor_email_mapping para o cliente {} (Eduardo Bisinella).", sale.customerUuid());
                    }
                    log.warn("Sem mapeamento para customer UUID {}. Venda {} ignorada.",
                            sale.customerUuid(),
                            saleIdToProcess);
                    continue;
                }

                String recipientEmail = resolveRecipientEmail(mapping);
                if (!StringUtils.hasText(recipientEmail)) {
                    skippedMapping++;
                    log.warn("Mapeamento sem e-mail de destino (user/fallback) para customer UUID {}. Venda {} ignorada.",
                        sale.customerUuid(),
                        saleIdToProcess);
                    continue;
                }

                String doctorName = resolveDoctorName(mapping, sale.customerName());

                byte[] pdfBytes = contaAzulClient.downloadSalePdf(saleIdToProcess);
                financeEmailService.sendReceiptEmailWithPdf(
                    doctorName,
                    recipientEmail,
                    buildEmailBody(sale, doctorName),
                        pdfBytes,
                        "recibo-venda-" + saleIdToProcess + ".pdf");

                log.info("E-mail enviado com sucesso para venda {} (médico: {}).",
                    saleIdToProcess,
                    StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

                processedSaleRepository.save(ProcessedSale.builder()
                        .saleId(saleIdToProcess)
                        .build());

                log.info("Venda {} registrada como processada com sucesso.", saleIdToProcess);

                sent++;
            } catch (DataIntegrityViolationException ex) {
                skippedProcessed++;
                log.debug("Venda {} já registrada como processada em execução concorrente.", sale.saleId());
            } catch (RuntimeException ex) {
                failures++;
                log.error("Falha ao processar venda {}.", sale.saleId(), ex);
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

    private String buildEmailBody(ContaAzulClient.SaleItem sale, String doctorName) {
        return "Olá " + doctorName
                + ",\n\nSeu recibo financeiro referente à venda "
                + sale.saleId()
                + " foi liquidado na Conta Azul e segue em anexo.\n\n"
                + "Atenciosamente,\nInovare TI";
    }

    private String extractSaleNumberFromDescription(String descricao) {
        if (!StringUtils.hasText(descricao)) {
            return null;
        }

        Matcher matcher = SALE_NUMBER_FROM_DESCRIPTION_PATTERN.matcher(descricao.trim());
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private Map<String, String> buildSaleNumberToUuidIndex(List<ContaAzulClient.SaleItem> sales) {
        Map<String, String> index = new HashMap<>();

        for (ContaAzulClient.SaleItem sale : sales) {
            if (!StringUtils.hasText(sale.saleNumber()) || !StringUtils.hasText(sale.saleId())) {
                continue;
            }

            index.put(sale.saleNumber().trim(), sale.saleId().trim());
        }

        return index;
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

    public record TesteEnvioRealResult(
            String saleId,
            String doctorName,
            String recipientEmail,
            int pdfBytes) {
    }

        public record SyncDoctorsResult(
            int novos,
            int atualizados) {
        }
}