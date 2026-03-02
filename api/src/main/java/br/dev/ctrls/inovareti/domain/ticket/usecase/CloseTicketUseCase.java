package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: fecha um chamado aberto.
 * Regra de negócio crítica:
 *   Se o chamado tiver {@code requestedItem} e {@code requestedQuantity},
 *   o {@code currentStock} do item é decrementado atomicamente na mesma transação.
 */
@Component
@RequiredArgsConstructor
public class CloseTicketUseCase {

    private final TicketRepository ticketRepository;
    private final ItemRepository itemRepository;

    /**
     * Fecha o chamado e, se houver item solicitado, debita o estoque.
     *
     * @param ticketId UUID do chamado a ser fechado
     * @return DTO com os dados atualizados do chamado
     * @throws NotFoundException     se o chamado não for encontrado
     * @throws IllegalStateException se o estoque for insuficiente
     */
    @Transactional
    public TicketResponseDTO execute(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException(
                        "Chamado não encontrado com o id: " + ticketId));

        // Debita o estoque caso o chamado tenha item solicitado
        if (ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
            var item = ticket.getRequestedItem();
            int novoEstoque = item.getCurrentStock() - ticket.getRequestedQuantity();

            if (novoEstoque < 0) {
                throw new IllegalStateException(
                        "Estoque insuficiente para o item '" + item.getName()
                        + "'. Estoque atual: " + item.getCurrentStock()
                        + ", quantidade solicitada: " + ticket.getRequestedQuantity());
            }

            item.setCurrentStock(novoEstoque);
            itemRepository.save(item);
        }

        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());

        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }
}
