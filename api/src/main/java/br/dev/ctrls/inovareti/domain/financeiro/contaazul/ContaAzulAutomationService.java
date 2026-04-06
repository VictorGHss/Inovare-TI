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
        return contaAzulSyncService.syncAllDoctorsFromContaAzul();
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
        contaAzulReceiptProcessor.processCurrentMonthAcquittedSales();
    }

    public ContaAzulReceiptProcessor.ReceiptProcessingResult processAcquittedSales(LocalDate dataInicio, LocalDate dataFim) {
        return contaAzulReceiptProcessor.processAcquittedSales(dataInicio, dataFim);
    }
}