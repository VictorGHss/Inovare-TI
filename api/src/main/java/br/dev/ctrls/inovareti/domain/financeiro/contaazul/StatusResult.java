package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Record para representação de total e disponibilidade por status.
 */
public record StatusResult(long total, boolean available) {
}
