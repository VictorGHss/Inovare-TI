package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.ArrayList;
import java.util.List;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulAutomationService {

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;
    private final ProcessedSaleRepository processedSaleRepository;
    private final FinanceEmailService financeEmailService;

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

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales();
        } catch (RuntimeException ex) {
            log.error("Falha ao buscar vendas liquidadas no Conta Azul.", ex);
            return;
        }

        log.info("Pooling Conta Azul: {} venda(s) mapeadas a partir de parcelas recebidas.", acquittedSales.size());

        int sent = 0;
        int skippedProcessed = 0;
        int skippedMapping = 0;
        int failures = 0;

        for (ContaAzulClient.SaleItem sale : acquittedSales) {
            try {
                log.info("Processando venda {} do médico {}.",
                        sale.saleId(),
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

                if (processedSaleRepository.existsBySaleId(sale.saleId())) {
                    skippedProcessed++;
                    log.info("Venda {} já processada anteriormente. Ignorando.", sale.saleId());
                    continue;
                }

                if (!StringUtils.hasText(sale.customerUuid())) {
                    skippedMapping++;
                    log.warn("Venda {} sem customer UUID. Pulando.", sale.saleId());
                    continue;
                }

                DoctorEmailMapping mapping = doctorEmailMappingRepository
                        .findByContaAzulCustomerUuid(sale.customerUuid())
                        .orElse(null);

                if (mapping == null) {
                    skippedMapping++;
                    log.warn("Sem mapeamento para customer UUID {}. Venda {} ignorada.",
                            sale.customerUuid(),
                            sale.saleId());
                    continue;
                }

                String recipientEmail = resolveRecipientEmail(mapping);
                if (!StringUtils.hasText(recipientEmail)) {
                    skippedMapping++;
                    log.warn("Mapeamento sem e-mail de destino (user/fallback) para customer UUID {}. Venda {} ignorada.",
                        sale.customerUuid(),
                        sale.saleId());
                    continue;
                }

                String doctorName = resolveDoctorName(mapping, sale.customerName());

                byte[] pdfBytes = contaAzulClient.downloadSalePdf(sale.saleId());
                financeEmailService.sendReceiptEmailWithPdf(
                    doctorName,
                    recipientEmail,
                    buildEmailBody(sale, doctorName),
                        pdfBytes,
                        "recibo-venda-" + sale.saleId() + ".pdf");

                log.info("E-mail enviado com sucesso para venda {} (médico: {}).",
                    sale.saleId(),
                    StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional");

                processedSaleRepository.save(ProcessedSale.builder()
                        .saleId(sale.saleId())
                        .build());

                log.info("Venda {} registrada como processada com sucesso.", sale.saleId());

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
}