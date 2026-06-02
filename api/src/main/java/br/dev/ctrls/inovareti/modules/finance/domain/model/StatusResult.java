package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * Record para representação de total e disponibilidade por status.
 */
public record StatusResult(long total, boolean available) {
}

