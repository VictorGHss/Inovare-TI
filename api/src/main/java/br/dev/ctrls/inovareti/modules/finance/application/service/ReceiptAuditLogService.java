package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulReceiptRetryPolicy;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ReceiptConcurrencyHandler;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÂ§o especialista em auditoria, histÃƒÂ³rico e registros de processamento
 * de recibos da Conta Azul.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptAuditLogService {

    private static final int MAX_ERROR_DETAILS = 200;

    private final ContaAzulReceiptRetryPolicy retryPolicy;
    private final ReceiptConcurrencyHandler concurrencyHandler;

    /**
     * Registra o inÃƒÂ­cio do processamento de uma parcela.
     */
    public void logStart(String descricao, String origem, String parcelaId) {
        log.info("[INICIO PROCESSAMENTO] Parcela: {}", StringUtils.hasText(descricao) ? descricao.trim() : "(sem descriÃƒÂ§ÃƒÂ£o)");
        log.info("[ORIGEM] origem={} | parcelaId={}", StringUtils.hasText(origem) ? origem : "(nula)", StringUtils.hasText(parcelaId) ? parcelaId : "(sem id)");
    }

    /**
     * Registra o processamento bem-sucedido de um recibo.
     */
    public void recordSuccess(String baixaId, String doctorName, boolean usedInternalFallback, String customerName) {
        retryPolicy.clearAttempts(baixaId);

        String resolvedName = StringUtils.hasText(customerName) ? customerName : "Profissional";
        if (usedInternalFallback) {
            log.info("Recibo interno enviado com sucesso para baixa {} (mÃƒÂ©dico: {}).", baixaId, resolvedName);
        }

        log.info("E-mail enviado com sucesso para recibo {} (mÃƒÂ©dico: {}).", baixaId, resolvedName);

        concurrencyHandler.markAsProcessed(baixaId,
                "Recibo " + baixaId + " registrado como processado com sucesso.",
                "Recibo " + baixaId + " jÃƒÂ¡ registrado por concorrÃƒÂªncia ao finalizar processamento.");
    }

    /**
     * Registra uma falha SMTP de e-mail.
     */
    public void recordEmailFailure(String baixaId, String resolvedSaleId, String doctorName, Exception emailEx, List<String> errors) {
        String details = "Falha SMTP no envio do recibo da baixa " + baixaId
                + ". O processamento da parcela foi preservado no banco para nÃƒÂ£o perder trabalho. Erro: "
                + emailEx.getMessage();
        registerError(errors, details);
        retryPolicy.handleEmailFailure(baixaId, resolvedSaleId, doctorName, details);
    }

    /**
     * Registra uma falha de download do recibo.
     */
    public void recordDownloadFailure(String baixaId, String resolvedSaleId, String doctorName, Exception ex, List<String> errors) {
        String details = "Falha ao baixar recibo para baixa " + baixaId + ": " + ex.getMessage();
        boolean failedPermanently = retryPolicy.registerAttemptAndCheckIfPermanentFailure(baixaId, resolvedSaleId, doctorName, details);
        if (failedPermanently) {
            log.error("Recibo da baixa {} falhou repetidamente. Marcado como processado e alerta registrado.", baixaId);
        }
        registerError(errors, details);
    }

    /**
     * Registra uma falha no fallback de geraÃƒÂ§ÃƒÂ£o de recibo interno.
     */
    public void recordFallbackFailure(String baixaId, Exception fallbackEx, List<String> errors) {
        log.error("Falha ao gerar recibo interno para baixa {}.", baixaId, fallbackEx);
        registerError(errors, "Falha ao gerar recibo interno para baixa " + baixaId + ": " + fallbackEx.getMessage());
    }

    /**
     * Registra falha de PDF vazio/nulo.
     */
    public void recordEmptyPdfFailure(String baixaId, String resolvedSaleId, String doctorName, List<String> errors) {
        String details = "Recibo da baixa " + baixaId + " retornou PDF vazio. TentarÃƒÂ¡ novamente.";
        boolean failedPermanently = retryPolicy.registerAttemptAndCheckIfPermanentFailure(baixaId, resolvedSaleId, doctorName, details);
        if (failedPermanently) {
            log.error("Recibo da baixa {} nÃƒÂ£o gerou bytes apÃƒÂ³s limite de tentativas. Marcado como processado e alerta registrado.", baixaId);
        }
        registerError(errors, "Recibo da baixa " + baixaId + " retornou PDF vazio. TentarÃƒÂ¡ novamente.");
    }

    /**
     * Registra um alerta/erro de baixaId invÃƒÂ¡lido.
     */
    public void recordInvalidBaixaId(String parcelaId, List<String> errors) {
        log.warn("baixaId invÃƒÂ¡lido (nulo/vazio) para parcela {}. Item ignorado para seguranÃƒÂ§a.", parcelaId);
        registerError(errors, "baixaId invÃƒÂ¡lido para parcela " + parcelaId + ". Item ignorado para seguranÃƒÂ§a.");
    }

    /**
     * Registra quando nenhuma baixa ÃƒÂ© encontrada para a parcela.
     */
    public void recordNoBaixaFound(String parcelaId, String resolvedSaleId, List<String> errors) {
        log.error("Nenhuma baixa encontrada para a parcela {}. Marcando como processado para evitar loop.", parcelaId);
        registerError(errors, "Nenhuma baixa encontrada para a parcela " + parcelaId + ". Item nÃƒÂ£o processado.");

        String markId = StringUtils.hasText(parcelaId)
                ? parcelaId
                : (StringUtils.hasText(resolvedSaleId) ? resolvedSaleId : null);

        if (markId != null) {
            concurrencyHandler.markAsProcessed(markId,
                    "Recibo " + markId + " registrado como processado (nenhuma baixa encontrada).",
                    "Recibo " + markId + " jÃƒÂ¡ registrado por concorrÃƒÂªncia ao marcar como processado quando nenhuma baixa encontrada.");
        } else {
            log.warn("Nenhum identificador disponÃƒÂ­vel para marcar como processado (parcela sem id e sem saleId).");
        }
    }

    /**
     * Auxiliar para adicionar erros respeitando o limite mÃƒÂ¡ximo.
     */
    public void registerError(List<String> errors, String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }

        if (errors.size() >= MAX_ERROR_DETAILS) {
            return;
        }

        errors.add(message.trim());
    }
}

