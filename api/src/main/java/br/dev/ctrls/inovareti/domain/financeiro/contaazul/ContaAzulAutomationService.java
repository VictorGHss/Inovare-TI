package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
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
    private static final Pattern SALE_NUMBER_FROM_DESCRIPTION_PATTERN = Pattern.compile("^Venda\\s+(\\d+)\\b.*$", Pattern.CASE_INSENSITIVE);

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
        // Para o teste real, buscar baixa da parcela e baixar recibo oficial
        Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
        if (baixaIdOpt.isEmpty()) {
            throw new IllegalStateException("Nenhuma baixa encontrada para a parcela da venda informada.");
        }
        byte[] pdfBytes = contaAzulClient.downloadReceiptPdf(baixaIdOpt.get());
        log.info("PDF baixado ({} bytes) para a venda {}.", pdfBytes.length, sale.saleId());

        log.info("Enviando para {}", recipientEmail);
        financeEmailService.sendReceiptEmailWithPdf(
                doctorName,
                recipientEmail,
                buildEmailBody(doctorName, StringUtils.hasText(sale.saleNumber()) ? sale.saleNumber() : "N/D"),
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
            log.info("Automação ContaAzul: token ainda não autorizado. Pulando execução.");
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

        // Novo fluxo: para cada parcela, buscar baixa e recibo via endpoint oficial
        int sent = 0;
        int skippedProcessed = 0;
        int skippedMapping = 0;
        int failures = 0;
        for (ContaAzulClient.SaleItem sale : acquittedSales) {
            try {
                if (!applyThrottle()) {
                    continue;
                }

                log.info("[INICIO PROCESSAMENTO] Parcela: {}", StringUtils.hasText(sale.descricao()) ? sale.descricao().trim() : "(sem descrição)");
                log.info("[ORIGEM] origem={} | parcelaId={}", StringUtils.hasText(sale.origem()) ? sale.origem() : "(nula)", StringUtils.hasText(sale.parcelaId()) ? sale.parcelaId() : "(sem id)");

                String customerUuidFromParcel = normalizeUuid(sale.customerUuid());
                log.info("Parcela recebida para processamento: parcelaId={}", StringUtils.hasText(sale.parcelaId()) ? sale.parcelaId() : "(sem id)");

                // Busca a baixa oficial da parcela
                Optional<String> baixaIdOpt = contaAzulClient.fetchBaixaIdByParcelaId(sale.parcelaId());
                if (baixaIdOpt.isEmpty()) {
                    log.warn("Nenhuma baixa encontrada para a parcela {}. Pulando item.", sale.parcelaId());
                    continue;
                }
                String baixaId = baixaIdOpt.get();

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
                    log.warn("[MAP_FAIL] Cadastro faltando para o médico: {}", StringUtils.hasText(sale.customerName()) ? sale.customerName() : "(nome indisponível)");
                    continue;
                }

                log.info("-> Médico no banco: {} (E-mail: {})", mapping.getDoctorName(), mapping.getDoctorEmail());

                String recipientEmail = resolveRecipientEmail(mapping);
                if (!StringUtils.hasText(recipientEmail)) {
                    skippedMapping++;
                    log.warn("Mapeamento sem e-mail de destino (user/fallback) para customer UUID {}. Recibo {} ignorado.", customerUuidFromParcel, baixaId);
                    continue;
                }

                String doctorName = resolveDoctorName(mapping, sale.customerName());
                log.info("Médico identificado para a parcela {}: {}. Prosseguindo para baixar PDF do Recibo da Baixa {}", sale.parcelaId(), doctorName, baixaId);

                byte[] pdfBytes = contaAzulClient.downloadReceiptPdf(baixaId);
                if (pdfBytes.length == 0) {
                    log.warn("Download do recibo retornou vazio para a baixa {}.", baixaId);
                    continue;
                }

                financeEmailService.sendReceiptEmailWithPdf(
                    doctorName,
                    recipientEmail,
                    buildEmailBody(doctorName, StringUtils.hasText(sale.saleNumber()) ? sale.saleNumber() : "N/D"),
                    pdfBytes,
                    "recibo-quitacao-baixa-" + baixaId + ".pdf");

                log.info("E-mail enviado com sucesso para recibo {} (médico: {}).", baixaId, StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

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

        log.info("Automação ContaAzul finalizada: acquitted={}, sent={}, skippedProcessed={}, skippedMapping={}, failures={}", acquittedSales.size(), sent, skippedProcessed, skippedMapping, failures);
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

    private String buildEmailBody(String doctorName, String saleNumber) {
        return "Olá " + doctorName
                + ",\n\nSegue em anexo o seu recibo #" + saleNumber + ".\n\n"
                + "Este é um envio automático do sistema Inovare TI.\n\n"
                + "Atenciosamente,\nAdministrativo Inovare.";
    }

    // Método legado, não utilizado mais para recibo
    private String extractSaleNumberFromDescription(String descricao) {
        return null;
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

    // Novo fluxo: enriquecimento não é mais necessário, pois recibo é obtido via baixa financeira
}