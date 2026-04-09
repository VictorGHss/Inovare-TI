package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulAutomationService {
    private final ContaAzulSyncService contaAzulSyncService;
    private final ContaAzulReceiptProcessor contaAzulReceiptProcessor;

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

    @Value("${CONTAAZUL_AUTOMATION_FIXED_DELAY_MS:300000}")
    private long contaAzulPollingIntervalMs;

    @Scheduled(
            fixedDelayString = "${CONTAAZUL_AUTOMATION_FIXED_DELAY_MS:300000}",
            initialDelayString = "${app.contaazul.automation.initial-delay-ms:180000}")
    public void processAcquittedSales() {
        log.info("Ciclo de busca ContaAzul iniciado. Intervalo configurado: {}ms", contaAzulPollingIntervalMs);
        log.info("Próxima execução prevista em {} minutos.", contaAzulPollingIntervalMs / 60000);
        try {
            // Protege o ciclo agendado: falha externa da Conta Azul não deve derrubar a execução da API.
            contaAzulReceiptProcessor.processCurrentMonthAcquittedSales();
        } catch (RuntimeException ex) {
            log.error("Falha na automação agendada da Conta Azul. A execução foi preservada e seguirá no próximo ciclo.", ex);
        }
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