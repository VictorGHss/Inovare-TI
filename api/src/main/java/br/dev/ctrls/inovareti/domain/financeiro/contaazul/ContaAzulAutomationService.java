package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMapping;
import br.dev.ctrls.inovareti.domain.financeiro.DoctorEmailMappingRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSale;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessingAttempt;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessingAttemptRepository;
import br.dev.ctrls.inovareti.domain.notification.FinanceEmailService;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulAutomationService {
    /**
     * Serviço responsável por orquestrar automações relacionadas à integração com a
     * Conta Azul. Contém rotinas para:
     * - sincronizar cadastro de médicos/clientes (pessoas) da Conta Azul para o banco local;
     * - executar testes de envio real de recibos (baixar PDF da baixa e enviar por e-mail);
     * - processar vendas liquidadas (pooling/agendamento), baixar recibos e enviar por e-mail,
     *   além de registrar idempotência e tentativas de processamento.
     *
     * Observações:
     * - A automação respeita a flag `app.contaazul.automation.enabled` e checa se a
     *   configuração de vendas e o token estão disponíveis antes de executar.
     */

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    /** Formato ISO para datas usadas nas consultas de período (YYYY-MM-DD). */

    private final ContaAzulClient contaAzulClient;
    private final ContaAzulTokenService contaAzulTokenService;
    private final DoctorEmailMappingRepository doctorEmailMappingRepository;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
    private final FinanceEmailService financeEmailService;
    private final UserRepository userRepository;

    public SyncDoctorsResult syncAllDoctorsFromContaAzul() {
            /**
             * Sincroniza todos os registros de pessoas (clientes/médicos) provenientes da Conta Azul
             * com o repositório local de mapeamentos. Para cada pessoa retornada pelo cliente ContaAzul:
             * - ignora itens sem id ou e-mail;
             * - tenta encontrar um usuário local pelo e-mail;
             * - cria ou atualiza o mapeamento `DoctorEmailMapping` associando o customer UUID ao e-mail/usuário.
             *
             * Retorna um resumo com a quantidade de mapeamentos criados e atualizados.
             */
        // Recupera todas as pessoas do ContaAzul e itera para criar/atualizar
        // os mapeamentos locais entre customer UUID e usuário/e-mail.
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
            /**
             * Executa um envio de teste real para uma venda específica identificada por `saleId`.
             * Fluxo:
             * 1. valida se há configuração de vendas e token autorizado;
             * 2. recupera a venda com status ACQUITTED;
             * 3. verifica mapeamento do customer UUID para localizar e-mail do destinatário;
             * 4. baixa o PDF do recibo da baixa e envia por e-mail ao destinatário.
             *
             * Lança `IllegalStateException` ou `IllegalArgumentException` quando pré-condições
             * não são atendidas (configuração ausente, token não autorizado, mapeamento ausente, etc.).
             */
        // Fluxo de teste real: valida pré-condições, localiza mapeamento e envia o recibo.
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
            fixedDelayString = "${app.contaazul.automation.fixed-delay-ms:30000}",
            initialDelayString = "${app.contaazul.automation.initial-delay-ms:180000}")
    public void processAcquittedSales() {
            /**
             * Processa vendas liquidadas (acquitted) no período informado. Para cada parcela encontrada:
             * - aplica throttling para evitar 429 do ERP;
             * - tenta localizar a baixa financeira associada à parcela;
             * - verifica se já foi processada (idempotência);
             * - tenta baixar o recibo PDF; em caso de indisponibilidade do recibo, registra tentativas
             *   e, após N tentativas, marca como processado para evitar loops infinitos;
             * - envia e-mail com o recibo ao destinatário resolvido via mapeamento ou usuário associado.
             *
             * Este método é seguro para ser chamado tanto pela rotina agendada quanto manualmente
             * (útil para testes e execuções ad-hoc) e é resiliente a falhas por parcela — falhas são
             * contabilizadas e não interrompem a execução completa.
             */
        // Método agendado que processa vendas quitadas: baixa recibos, envia e-mails
        // e registra idempotência. Projetado para ser resiliente a falhas por item.
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
            log.debug("Automação ContaAzul: token ainda não autorizado. Pulando execução.");
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
                    log.error("Nenhuma baixa encontrada para a parcela {}. Marcando como processado para evitar loop.", sale.parcelaId());
                    String markId = StringUtils.hasText(sale.parcelaId()) ? sale.parcelaId() : (StringUtils.hasText(sale.saleId()) ? sale.saleId() : null);
                    if (markId != null) {
                        try {
                            processedSaleRepository.save(ProcessedSale.builder().saleId(markId).build());
                            log.info("Recibo {} registrado como processado (nenhuma baixa encontrada).", markId);
                        } catch (DataIntegrityViolationException ex) {
                            log.debug("Recibo {} já registrado por concorrência ao marcar como processado quando nenhuma baixa encontrada.", markId);
                        }
                    } else {
                        log.warn("Nenhum identificador disponível para marcar como processado (parcela sem id e sem saleId). Parcela descrição: {}", sale.descricao());
                    }
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

                byte[] pdfBytes;
                try {
                    pdfBytes = contaAzulClient.downloadReceiptPdf(baixaId);
                } catch (br.dev.ctrls.inovareti.domain.financeiro.contaazul.NoReceiptAvailableException nr) {
                    // Recibo ainda não gerado pelo ERP — contabiliza tentativa e tenta novamente depois
                    ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
                    if (attempt == null) {
                        processingAttemptRepository.save(ProcessingAttempt.builder().saleId(baixaId).attempts(1).build());
                        log.info("Recibo ainda não gerado pelo ERP para a baixa {}. Tentando novamente na próxima execução.", baixaId);
                    } else {
                        int attempts = attempt.getAttempts() + 1;
                        attempt.setAttempts(attempts);
                        processingAttemptRepository.save(attempt);
                        if (attempts >= 5) {
                            try {
                                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                                processingAttemptRepository.deleteBySaleId(baixaId);
                                log.error("Recibo da baixa {} não gerado após {} tentativas. Marcado como processado.", baixaId, attempts);
                            } catch (DataIntegrityViolationException dex) {
                                log.debug("Recibo {} já registrado por concorrência ao marcar como processado após tentativas.", baixaId);
                            }
                        } else {
                            log.info("Recibo ainda não gerado pelo ERP para a baixa {}. Tentando novamente na próxima execução. Tentativa {}/5", baixaId, attempts);
                        }
                    }
                    continue;
                } catch (RuntimeException ex) {
                    // Outros erros — contabiliza tentativa similarmente
                    ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
                    if (attempt == null) {
                        processingAttemptRepository.save(ProcessingAttempt.builder().saleId(baixaId).attempts(1).build());
                    } else {
                        int attempts = attempt.getAttempts() + 1;
                        attempt.setAttempts(attempts);
                        processingAttemptRepository.save(attempt);
                        if (attempts >= 5) {
                            try {
                                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                                processingAttemptRepository.deleteBySaleId(baixaId);
                                log.error("Recibo da baixa {} falhou repetidamente ({} tentativas). Marcado como processado.", baixaId, attempts);
                            } catch (DataIntegrityViolationException dex) {
                                log.debug("Recibo {} já registrado por concorrência ao marcar como processado após falhas.", baixaId);
                            }
                        }
                    }
                    failures++;
                    log.error("Falha ao baixar recibo para baixa {}.", baixaId, ex);
                    continue;
                }

                if (pdfBytes == null || pdfBytes.length == 0) {
                    // Sem bytes — conta como não gerado ainda
                    ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
                    if (attempt == null) {
                        processingAttemptRepository.save(ProcessingAttempt.builder().saleId(baixaId).attempts(1).build());
                    } else {
                        int attempts = attempt.getAttempts() + 1;
                        attempt.setAttempts(attempts);
                        processingAttemptRepository.save(attempt);
                        if (attempts >= 5) {
                            try {
                                processedSaleRepository.save(ProcessedSale.builder().saleId(baixaId).build());
                                processingAttemptRepository.deleteBySaleId(baixaId);
                                log.error("Recibo da baixa {} não gerou bytes após {} tentativas. Marcado como processado.", baixaId, attempts);
                            } catch (DataIntegrityViolationException dex) {
                                log.debug("Recibo {} já registrado por concorrência ao marcar como processado após tentativas.", baixaId);
                            }
                        } else {
                            log.info("Recibo ainda não gerado pelo ERP para a baixa {}. Tentando novamente na próxima execução. Tentativa {}/5", baixaId, attempt.getAttempts());
                        }
                    }
                    continue;
                }

                // Sucesso: limpa tentativas e envia email
                processingAttemptRepository.deleteBySaleId(baixaId);

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
            // Resolve o e-mail de destinatário preferindo o e-mail do usuário associado,
            // caindo para o e-mail cadastrado no mapeamento quando necessário.
        if (mapping.getUser() != null && StringUtils.hasText(mapping.getUser().getEmail())) {
            return mapping.getUser().getEmail();
        }

        return mapping.getDoctorEmail();
    }

    private String resolveDoctorName(DoctorEmailMapping mapping, String customerName) {
            // Resolve o nome do profissional: primeiro do usuário associado, depois do mapeamento,
            // finalmente usa o nome vindo do payload (customerName) ou um valor padrão.
        if (mapping.getUser() != null && StringUtils.hasText(mapping.getUser().getName())) {
            return mapping.getUser().getName();
        }

        if (StringUtils.hasText(mapping.getDoctorName())) {
            return mapping.getDoctorName();
        }

        return StringUtils.hasText(customerName) ? customerName : "Profissional";
    }

    private Optional<DoctorEmailMapping> findDoctorMappingByCustomerUuid(String customerUuidFromParcel) {
            // Tenta localizar mapeamento do médico a partir do UUID do cliente/parcela.
            // Faz normalização e várias tentativas de correspondência (normalizada, direta e por busca completa).
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
            // Normaliza UUIDs/textos para comparação: trim + lower-case.
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private boolean applyThrottle() {
            // Aplica um pequeno delay entre chamadas externas para reduzir risco de 429.
        LockSupport.parkNanos(350_000_000L);

        if (Thread.currentThread().isInterrupted()) {
            log.warn("Thread interrompida durante throttling anti-429 da automação financeira.");
            return false;
        }

        return true;
    }


    private String buildEmailBody(String doctorName, String receiptNumber) {
            // Constrói o corpo do e-mail enviado ao profissional com o recibo em anexo.
        return "Olá " + doctorName
                + ",\n\nSegue em anexo o seu recibo de quitação (baixa) número: " + receiptNumber + ".\n\n"
                + "Este é um envio automático do sistema Inovare TI.\n\n"
                + "Atenciosamente,\nAdministrativo Inovare.";
    }


    private String buildSalesConfigurationErrorMessage() {
            // Monta uma mensagem explicativa quando as propriedades necessárias para integração
            // de vendas com a Conta Azul estão incompletas.
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