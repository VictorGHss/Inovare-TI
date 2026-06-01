package br.dev.ctrls.inovareti.modules.finance.domain.model;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivableParcelRef;

import java.util.List;

/**
 * Record para representaÃƒÂ§ÃƒÂ£o do resultado agregado de busca de parcelas recebidas.
 */
public record ReceivedParcelsResult(
        List<ReceivableParcelRef> parcels,
        boolean available,
        String lastUpdatedAt) {
}

