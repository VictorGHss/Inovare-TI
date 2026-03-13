package br.dev.ctrls.inovareti.domain.financeiro;

public enum ProcessedReceiptStatus {
    SENT,
    SKIPPED_DUPLICATE,
    FAILED,
    PENDING_RETRY,
    HISTORICO
}
