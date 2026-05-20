package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSale;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessingAttempt;
import br.dev.ctrls.inovareti.domain.financeiro.ProcessingAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de domínio puro (Domain Service) responsável por gerenciar e aplicar a política de retry (tentativas)
 * e o ciclo de falhas críticas ou permanentes de processamento de recibos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulReceiptRetryPolicy {

    private static final int MAX_RETRIES = 20;

    private final ProcessingAttemptRepository processingAttemptRepository;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ReceiptAlertService receiptAlertService;

    /**
     * Registra/incrementa uma tentativa de processamento falha no banco de dados.
     * Caso o limite de tentativas seja atingido, encerra o ciclo marcando o recibo como processado
     * e dispara um alerta de falha permanente administrativo.
     *
     * @return true se o limite foi atingido (falha permanente), false caso contrário.
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
            // Salva marcador para evitar reprocessamentos infinitos e concorrência no loop
            saveProcessedSaleIfNeeded(baixaId,
                    "Recibo {} marcado como processado após limite máximo de falhas recorrentes.",
                    "Recibo {} já registrado por concorrência ao marcar como processado após limite de falhas.");

            // Limpa as tentativas da tabela de retries
            processingAttemptRepository.deleteBySaleId(baixaId);

            // Notifica o erro crítico e permanente via canais administrativos
            receiptAlertService.notifyPermanentReceiptFailure(
                    baixaId,
                    resolvedSaleId,
                    doctorName,
                    attempts,
                    details);

            return true;
        }

        log.info("Recibo ainda não gerado ou falhou para a baixa {}. Tentando novamente na próxima execução. Tentativa {}/{}",
                baixaId, attempts, MAX_RETRIES);
        return false;
    }

    /**
     * Trata o cenário de falha SMTP no envio de e-mails para preservar o trabalho já realizado.
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
                "Recibo {} já estava marcado como processado ao tratar falha de e-mail SMTP.");

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
     * Limpa o rastro de tentativas de uma baixa após sucesso do processamento concorrente.
     */
    public void clearAttempts(String baixaId) {
        if (StringUtils.hasText(baixaId)) {
            processingAttemptRepository.deleteBySaleId(baixaId.trim());
        }
    }

    /**
     * Salva a venda processada na tabela de controle de concorrência se necessário.
     */
    public void saveProcessedSaleIfNeeded(String saleId, String successMessage, String duplicateMessage) {
        if (!StringUtils.hasText(saleId)) {
            return;
        }

        if (processedSaleRepository.existsBySaleId(saleId)) {
            if (StringUtils.hasText(duplicateMessage)) {
                log.debug(duplicateMessage, saleId);
            }
            return;
        }

        try {
            processedSaleRepository.save(ProcessedSale.builder().saleId(saleId).build());
            if (StringUtils.hasText(successMessage)) {
                log.info(successMessage, saleId);
            }
        } catch (DataIntegrityViolationException ex) {
            if (StringUtils.hasText(duplicateMessage)) {
                log.debug(duplicateMessage, saleId);
            }
        }
    }
}
