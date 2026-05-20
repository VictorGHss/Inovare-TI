package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;

/**
 * Record para representação do resultado agregado de busca de parcelas recebidas.
 */
public record ReceivedParcelsResult(
        List<ReceivableParcelRef> parcels,
        boolean available,
        String lastUpdatedAt) {
}
