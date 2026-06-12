package br.dev.ctrls.inovareti.modules.finance.domain.model;

public enum ProcessedReceiptStatus {
    SENT,
    SKIPPED_DUPLICATE,
    FAILED,
    PENDING_RETRY,
    HISTORICO,
    /** Falha temporária ou de rede na integração com a Conta Azul, passível de retentativa assíncrona. */
    FAILED_RETRYABLE,
    /** Falha definitiva na integração que foi movida para auditoria (Dead-Letter Queue lógica). */
    FAILED_PERMANENT
}

