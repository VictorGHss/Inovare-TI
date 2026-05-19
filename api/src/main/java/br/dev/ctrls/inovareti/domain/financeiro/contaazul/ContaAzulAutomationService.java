package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
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
            // Protege a sincronização de médicos para que falhas externas não interrompam o serviço.
            return contaAzulSyncService.syncAllDoctorsFromContaAzul();
        } catch (RuntimeException ex) {
            log.error("Falha ao sincronizar médicos na Conta Azul. Retornando resultado vazio para manter API viva.", ex);
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

        log.info("Sistema iniciado com busca incremental. Último fechamento: [{}]", lastClosingFormatted);
    }

    @Scheduled(cron = "#{@contaAzulProperties.automation.cron}")
    public void processAcquittedSales() {
        LocalDate today = LocalDate.now();
        if (!shouldRunClosing(today)) {
            // Regra corporativa: em meses comuns, o fechamento só deve ocorrer no dia 30.
            log.info("Aguardando dia 30 para fechamento");
            return;
        }

        LocalDate startDate = resolveIncrementalStartDate(today);
        LocalDate endDate = today;

        log.info("Ciclo incremental ContaAzul iniciado com cron {}. Janela: {} a {}.", properties.getAutomation().getCron(), startDate, endDate);
        try {
            // Busca incremental: retoma do último processed_at para evitar lacunas entre fechamentos.
            contaAzulReceiptProcessor.processAcquittedSales(startDate, endDate);
        } catch (RuntimeException ex) {
            log.error("Falha na automação agendada da Conta Azul. A execução foi preservada e seguirá no próximo ciclo.", ex);
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
            // Blindagem da execução manual: integração indisponível não deve propagar IllegalStateException.
            return contaAzulReceiptProcessor.processAcquittedSales(dataInicio, dataFim);
        } catch (RuntimeException ex) {
            log.error("Falha ao processar automação financeira no período [{} - {}]. Retornando resultado vazio.", dataInicio, dataFim, ex);
            return ContaAzulReceiptProcessor.ReceiptProcessingResult.empty();
        }
    }
}