package br.dev.ctrls.inovareti.modules.finance.domain.model;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivableParcelRef;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Record para representaÃƒÂ§ÃƒÂ£o dos dados de uma pÃƒÂ¡gina de parcelas a receber.
 */
public record ReceivablesPageData(
        List<ReceivableParcelRef> parcels,
        OffsetDateTime latestUpdate) {
}

