package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;

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

    @Value("${app.contaazul.automation.enabled:true}")
    private boolean automationEnabled;

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
            log.warn("Automação ContaAzul desabilitada: propriedades app.contaazul.sales-v2-url e app.contaazul.sales-pdf-v1-url-template são obrigatórias.");
            return;
        }

        if (!contaAzulTokenService.hasAuthorizedToken()) {
            log.info("Automação ContaAzul: token ainda não autorizado. Pulando execução.");
            return;
        }

        List<ContaAzulClient.SaleItem> acquittedSales;
        try {
            acquittedSales = contaAzulClient.fetchAcquittedSales();
        } catch (RuntimeException ex) {
            log.error("Falha ao buscar vendas liquidadas no Conta Azul.", ex);
            return;
        }

        log.info("Pooling Conta Azul: {} venda(s) com status ACQUITTED encontrada(s).", acquittedSales.size());

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

                if (mapping == null || !StringUtils.hasText(mapping.getDoctorEmail())) {
                    skippedMapping++;
                    log.warn("Sem mapeamento de e-mail para customer UUID {}. Venda {} ignorada.",
                            sale.customerUuid(),
                            sale.saleId());
                    continue;
                }

                byte[] pdfBytes = contaAzulClient.downloadSalePdf(sale.saleId());
                financeEmailService.sendReceiptEmailWithPdf(
                        StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional",
                        mapping.getDoctorEmail(),
                        buildEmailBody(sale),
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

    private String buildEmailBody(ContaAzulClient.SaleItem sale) {
        String doctorName = StringUtils.hasText(sale.customerName()) ? sale.customerName() : "Profissional";
        return "Olá " + doctorName
                + ",\n\nSeu recibo financeiro referente à venda "
                + sale.saleId()
                + " foi liquidado na Conta Azul e segue em anexo.\n\n"
                + "Atenciosamente,\nInovare TI";
    }
}