package br.dev.ctrls.inovareti.modules.finance.domain.model;


import java.time.OffsetDateTime;
import java.util.List;

/**
 * Record para representaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o dos dados de uma pÃƒÆ’Ã‚Â¡gina de parcelas a receber.
 */
public record ReceivablesPageData(
        List<ReceivableParcelRef> parcels,
        OffsetDateTime latestUpdate) {
}

