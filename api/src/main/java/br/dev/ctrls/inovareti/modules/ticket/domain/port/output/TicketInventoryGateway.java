package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;

import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;

/**
 * Interface que atua como porta local do subdomínio de Chamados para comunicação com o subdomínio de Inventário.
 * Essa abstração previne que os Casos de Uso de chamados acessem diretamente os adaptadores de persistência de inventário.
 */
public interface TicketInventoryGateway {

    /**
     * Verifica se um item de inventário existe pelo seu identificador único.
     *
     * @param id Identificador do item de inventário
     * @return true se o item existir, false caso contrário
     */
    boolean existsById(UUID id);

    /**
     * Recupera um item de inventário pelo seu identificador único.
     *
     * @param id Identificador do item de inventário
     * @return Optional contendo o item se for encontrado
     */
    Optional<Item> findById(UUID id);
}
