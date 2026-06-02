package br.dev.ctrls.inovareti.modules.finance.domain.model;


import java.util.List;

/**
 * Record para representação do resultado agregado de busca de parcelas recebidas.
 */
public record ReceivedParcelsResult(
        List<ReceivableParcelRef> parcels,
        boolean available,
        String lastUpdatedAt) {
}

