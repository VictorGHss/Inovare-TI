package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case: resolves a ticket.
 * Critical business rule:
 * If the ticket has requestedItem and requestedQuantity,
 * item currentStock is decremented atomically in the same transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResolveTicketUseCase {

    private final TicketRepository ticketRepository;
    private final ItemRepository itemRepository;
    private final CreateNotificationService createNotificationService;

    /**
     * Resolves a ticket and, if there is a requested item, debits inventory stock.
     *
     * @param ticketId UUID of the ticket to resolve
     * @return DTO with updated ticket data
     * @throws NotFoundException if ticket is not found
     * @throws IllegalStateException if inventory stock is insufficient
     */
    @Transactional
    public TicketResponseDTO execute(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException(
                        "Ticket not found with id: " + ticketId));

        if (ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
            var item = ticket.getRequestedItem();
            int newStock = item.getCurrentStock() - ticket.getRequestedQuantity();

            if (newStock < 0) {
                throw new IllegalStateException(
                        "Insufficient stock for item '" + item.getName()
                        + "'. Current stock: " + item.getCurrentStock()
                        + ", requested quantity: " + ticket.getRequestedQuantity());
            }

            item.setCurrentStock(newStock);
            itemRepository.save(item);
            log.info("Stock debited for ticket {}: item '{}', quantity: {}, new stock: {}",
                    ticketId, item.getName(), ticket.getRequestedQuantity(), newStock);
        }

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setClosedAt(LocalDateTime.now());

        Ticket resolvedTicket = ticketRepository.save(ticket);

        createNotificationService.create(
                resolvedTicket.getRequester().getId(),
                "Chamado Resolvido",
                "Seu chamado #" + resolvedTicket.getId().toString().substring(0, 8) + " foi resolvido",
                "/tickets/" + resolvedTicket.getId()
        );

        log.info("Ticket {} resolved successfully", ticketId);

        return TicketResponseDTO.from(resolvedTicket);
    }
}
