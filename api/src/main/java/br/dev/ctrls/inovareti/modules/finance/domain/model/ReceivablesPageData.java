package br.dev.ctrls.inovareti.modules.finance.domain.model;


import java.time.OffsetDateTime;
import java.util.List;

/**
 * Record para representação dos dados de uma página de parcelas a receber.
 */
public record ReceivablesPageData(
        List<ReceivableParcelRef> parcels,
        OffsetDateTime latestUpdate) {
}

