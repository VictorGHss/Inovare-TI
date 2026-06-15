package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketInventoryGateway;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de infraestrutura encarregado de implementar o gateway de acesso ao inventário.
 * Centraliza o consumo da porta de persistência do módulo de inventário, isolando o subdomínio de chamados.
 */
@Component
@RequiredArgsConstructor
public class TicketInventoryAdapter implements TicketInventoryGateway {

    private final ItemRepositoryPort itemRepositoryPort;

    @Override
    public boolean existsById(UUID id) {
        // Delega a verificação de existência ao repositório do módulo de inventário
        return itemRepositoryPort.existsById(id);
    }

    @Override
    public Optional<Item> findById(UUID id) {
        // Delega a busca pelo item ao repositório do módulo de inventário
        return itemRepositoryPort.findById(id);
    }
}
