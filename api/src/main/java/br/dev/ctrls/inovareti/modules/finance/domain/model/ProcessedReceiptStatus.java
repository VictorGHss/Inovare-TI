package br.dev.ctrls.inovareti.modules.finance.domain.model;

public enum ProcessedReceiptStatus {
    SENT,
    SKIPPED_DUPLICATE,
    FAILED,
    PENDING_RETRY,
    HISTORICO
}

