package br.dev.ctrls.inovareti.modules.finance.domain.model;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialAccountRef;

/**
 * Record para auditoria de saldo de conta financeira especÃƒÂ­fica.
 */
public record AccountBalanceAudit(
        FinancialAccountRef account,
        long balanceCents,
        boolean includedInBalance) {
}

