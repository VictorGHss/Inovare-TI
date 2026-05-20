package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Record para auditoria de saldo de conta financeira específica.
 */
public record AccountBalanceAudit(
        FinancialAccountRef account,
        long balanceCents,
        boolean includedInBalance) {
}
