package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Record para representação de referência de conta financeira.
 */
public record FinancialAccountRef(String accountId, String name, String type, boolean active) {
}
