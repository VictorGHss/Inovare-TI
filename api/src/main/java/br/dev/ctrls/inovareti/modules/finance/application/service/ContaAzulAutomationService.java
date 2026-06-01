package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulSyncService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.TesteEnvioRealResult;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedSaleRepository;
import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulReceiptProcessor;
import br.dev.ctrls.inovareti.modules.finance.domain.model.SyncDoctorsResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedSaleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulAutomationService {

    private static final DateTimeFormatter CLOSING_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ContaAzulSyncService contaAzulSyncService;
    private final ContaAzulReceiptProcessor contaAzulReceiptProcessor;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ContaAzulProperties properties;

    public SyncDoctorsResult syncAllDoctorsFromContaAzul() {
        try {
            // Protege a sincronizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de mÃƒÆ’Ã‚Â©dicos para que falhas externas nÃƒÆ’Ã‚Â£o interrompam o serviÃƒÆ’Ã‚Â§o.
            return contaAzulSyncService.syncAllDoctorsFromContaAzul();
        } catch (RuntimeException ex) {
            log.error("Falha ao sincronizar mÃƒÆ’Ã‚Â©dicos na Conta Azul. Retornando resultado vazio para manter API viva.", ex);
            return new SyncDoctorsResult(0, 0);
        }
    }

    public TesteEnvioRealResult processRealSaleTest(String saleId) {
        return contaAzulReceiptProcessor.processRealSaleTest(saleId);
    }

    @PostConstruct
    public void logIncrementalStartup() {
        LocalDateTime lastClosing = processedSaleRepository.findMaxProcessedAt().orElse(null);
        String lastClosingFormatted = lastClosing != null
                ? lastClosing.format(CLOSING_DATE_FORMATTER)
                : "sem registros";

        log.info("Sistema iniciado com busca incremental. ÃƒÆ’Ã…Â¡ltimo fechamento: [{}]", lastClosingFormatted);
    }

    /**
     * Rotina de fechamento mensal da Conta Azul.
     * Executa especificamente no dia 30 de cada mÃƒÆ’Ã‚Âªs (e no dia 28 em fevereiro) ÃƒÆ’Ã‚Â s 23:00.
     */
    @Scheduled(cron = "${app.contaazul.cron:0 0 23 28,30 * *}")
    public void processAcquittedSales() {
        LocalDate today = LocalDate.now();
        if (!shouldRunClosing(today)) {
            // Regra corporativa: em meses comuns, o fechamento sÃƒÆ’Ã‚Â³ deve ocorrer no dia 30.
            log.info("Aguardando dia 30 para fechamento");
            return;
        }

        LocalDate startDate = resolveIncrementalStartDate(today);
        LocalDate endDate = today;

        log.info("Ciclo incremental ContaAzul iniciado com cron {}. Janela: {} a {}.", properties.getAutomation().getCron(), startDate, endDate);
        try {
            // Busca incremental: retoma do ÃƒÆ’Ã‚Âºltimo processed_at para evitar lacunas entre fechamentos.
            contaAzulReceiptProcessor.processAcquittedSales(startDate, endDate);
        } catch (RuntimeException ex) {
            log.error("Falha na automaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o agendada da Conta Azul. A execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o foi preservada e seguirÃƒÆ’Ã‚Â¡ no prÃƒÆ’Ã‚Â³ximo ciclo.", ex);
        }
    }

    private boolean shouldRunClosing(LocalDate today) {
        int day = today.getDayOfMonth();
        if (day == 30) {
            return true;
        }

        return day == 28 && today.getMonth() == Month.FEBRUARY;
    }

    private LocalDate resolveIncrementalStartDate(LocalDate today) {
        return processedSaleRepository.findMaxProcessedAt()
                .map(LocalDateTime::toLocalDate)
                .orElse(today.minusMonths(1));
    }

    public ContaAzulReceiptProcessor.ReceiptProcessingResult processAcquittedSales(LocalDate dataInicio, LocalDate dataFim) {
        try {
            // Blindagem da execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o manual: integraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o indisponÃƒÆ’Ã‚Â­vel nÃƒÆ’Ã‚Â£o deve propagar IllegalStateException.
            return contaAzulReceiptProcessor.processAcquittedSales(dataInicio, dataFim);
        } catch (RuntimeException ex) {
            log.error("Falha ao processar automaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o financeira no perÃƒÆ’Ã‚Â­odo [{} - {}]. Retornando resultado vazio.", dataInicio, dataFim, ex);
            return ContaAzulReceiptProcessor.ReceiptProcessingResult.empty();
        }
    }
}

