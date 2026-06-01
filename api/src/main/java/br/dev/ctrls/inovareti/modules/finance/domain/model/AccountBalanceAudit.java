package br.dev.ctrls.inovareti.modules.finance.domain.model;


/**
 * Record para auditoria de saldo de conta financeira especÃƒÆ’Ã‚Â­fica.
 */
public record AccountBalanceAudit(
        FinancialAccountRef account,
        long balanceCents,
        boolean includedInBalance) {
}

