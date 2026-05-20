package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Record para representação dos dados de uma página de parcelas a receber.
 */
public record ReceivablesPageData(
        List<ReceivableParcelRef> parcels,
        OffsetDateTime latestUpdate) {
}
