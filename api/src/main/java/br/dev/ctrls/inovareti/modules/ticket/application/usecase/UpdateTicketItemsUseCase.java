package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.UpdateTicketItemsDTO;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketItemRequestEntity;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketInventoryGateway;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsável pela atualização (edição) de múltiplos itens do inventário de TI vinculados a um chamado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateTicketItemsUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final TicketInventoryGateway ticketInventoryGateway;

    /**
     * Executa a atualização dos itens associados a um chamado específico.
     * Limpa as associações anteriores e insere as novas caso o chamado não esteja resolvido.
     *
     * @param ticketId O identificador único do chamado (UUID)
     * @param request O DTO contendo a nova lista de itens e quantidades
     * @return O DTO com os dados atualizados do chamado
     * @throws NotFoundException Caso o chamado ou algum item informado não seja encontrado
     * @throws IllegalStateException Caso o chamado já se encontre resolvido
     */
    @Transactional
    public TicketResponseDTO execute(UUID ticketId, UpdateTicketItemsDTO request) {
        // 1. Recupera o chamado do banco de dados
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + ticketId));

        // 2. Valida se o chamado já foi resolvido (bloqueia alterações em chamados concluídos)
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new IllegalStateException("Não é possível alterar itens de um chamado que já está resolvido/fechado.");
        }

        // 3. Limpa a coleção antiga de itens vinculados (aproveitando o orphanRemoval=true)
        ticket.getRequestedItems().clear();

        // 4. Cria e valida os novos vínculos a partir do gateway de inventário
        var newRequestedItems = new ArrayList<TicketItemRequestEntity>();
        if (request.items() != null) {
            for (var dtoItem : request.items()) {
                // Busca o item no inventário através do gateway local para garantir desacoplamento
                Item item = ticketInventoryGateway.findById(dtoItem.itemId())
                        .orElseThrow(() -> new NotFoundException("Item de inventário não encontrado com id: " + dtoItem.itemId()));

                // Constrói a entidade associativa
                var itemRequest = TicketItemRequestEntity.builder()
                        .ticket(ticket)
                        .item(item)
                        .quantity(dtoItem.quantity())
                        .build();

                newRequestedItems.add(itemRequest);
            }
        }

        // 5. Associa a nova lista ao chamado
        ticket.getRequestedItems().addAll(newRequestedItems);

        // 6. Persiste as modificações e gera os logs correspondentes
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("[CHAMADO] Itens de inventário atualizados com sucesso para o chamado ID: {}", ticketId);

        return TicketResponseDTO.from(savedTicket);
    }
}
