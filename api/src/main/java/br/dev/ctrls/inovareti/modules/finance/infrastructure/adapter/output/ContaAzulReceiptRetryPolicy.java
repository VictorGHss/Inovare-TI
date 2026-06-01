package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.application.service.ReceiptAlertService;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ReceiptConcurrencyHandler;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessingAttempt;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessingAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÂ§o de domÃƒÂ­nio puro (Domain Service) responsÃƒÂ¡vel por gerenciar e aplicar a polÃƒÂ­tica de retry (tentativas)
 * e o ciclo de falhas crÃƒÂ­ticas ou permanentes de processamento de recibos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulReceiptRetryPolicy {

    private static final int MAX_RETRIES = 20;

    private final ProcessingAttemptRepository processingAttemptRepository;
    private final ReceiptAlertService receiptAlertService;
    private final ReceiptConcurrencyHandler concurrencyHandler;

    /**
     * Registra/incrementa uma tentativa de processamento falha no banco de dados.
     * Caso o limite de tentativas seja atingido, encerra o ciclo marcando o recibo como processado
     * e dispara um alerta de falha permanente administrativo.
     *
     * @return true se o limite foi atingido (falha permanente), false caso contrÃƒÂ¡rio.
     */
    public boolean registerAttemptAndCheckIfPermanentFailure(
            String baixaId,
            String resolvedSaleId,
            String doctorName,
            String details) {
        
        if (!StringUtils.hasText(baixaId)) {
            return false;
        }

        ProcessingAttempt attempt = processingAttemptRepository.findBySaleId(baixaId).orElse(null);
        int attempts = 1;

        if (attempt == null) {
            processingAttemptRepository.save(ProcessingAttempt.builder()
                    .saleId(baixaId)
                    .attempts(1)
                    .build());
        } else {
            attempts = attempt.getAttempts() + 1;
            attempt.setAttempts(attempts);
            processingAttemptRepository.save(attempt);
        }

        if (attempts >= MAX_RETRIES) {
            // Salva marcador para evitar reprocessamentos infinitos e concorrÃƒÂªncia no loop
            saveProcessedSaleIfNeeded(baixaId,
                    "Recibo {} marcado como processado apÃƒÂ³s limite mÃƒÂ¡ximo de falhas recorrentes.",
                    "Recibo {} jÃƒÂ¡ registrado por concorrÃƒÂªncia ao marcar como processado apÃƒÂ³s limite de falhas.");

            // Limpa as tentativas da tabela de retries
            processingAttemptRepository.deleteBySaleId(baixaId);

            // Notifica o erro crÃƒÂ­tico e permanente via canais administrativos
            receiptAlertService.notifyPermanentReceiptFailure(
                    baixaId,
                    resolvedSaleId,
                    doctorName,
                    attempts,
                    details);

            return true;
        }

        log.info("Recibo ainda nÃƒÂ£o gerado ou falhou para a baixa {}. Tentando novamente na prÃƒÂ³xima execuÃƒÂ§ÃƒÂ£o. Tentativa {}/{}",
                baixaId, attempts, MAX_RETRIES);
        return false;
    }

    /**
     * Trata o cenÃƒÂ¡rio de falha SMTP no envio de e-mails para preservar o trabalho jÃƒÂ¡ realizado.
     * Salva o recibo como processado e envia alerta permanente imediato.
     */
    public void handleEmailFailure(
            String baixaId,
            String resolvedSaleId,
            String doctorName,
            String details) {
        
        if (!StringUtils.hasText(baixaId)) {
            return;
        }

        log.error("Falha SMTP ao enviar recibo da baixa {}. Persistindo progresso sem envio de e-mail.", baixaId);

        saveProcessedSaleIfNeeded(baixaId,
                "Recibo {} marcado como processado mesmo com falha de e-mail SMTP.",
                "Recibo {} jÃƒÂ¡ estava marcado como processado ao tratar falha de e-mail SMTP.");

        // Limpa tentativas da tabela se houver
        processingAttemptRepository.deleteBySaleId(baixaId);

        // Envia alerta permanente imediato para notificar a falha SMTP
        receiptAlertService.notifyPermanentReceiptFailure(
                baixaId,
                resolvedSaleId,
                doctorName,
                1,
                details);
    }

    /**
     * Limpa o rastro de tentativas de uma baixa apÃƒÂ³s sucesso do processamento concorrente.
     */
    public void clearAttempts(String baixaId) {
        if (StringUtils.hasText(baixaId)) {
            processingAttemptRepository.deleteBySaleId(baixaId.trim());
        }
    }

    /**
     * Salva a venda processada na tabela de controle de concorrÃƒÂªncia se necessÃƒÂ¡rio.
     */
    public void saveProcessedSaleIfNeeded(String saleId, String successMessage, String duplicateMessage) {
        concurrencyHandler.markAsProcessed(saleId, successMessage, duplicateMessage);
    }
}


