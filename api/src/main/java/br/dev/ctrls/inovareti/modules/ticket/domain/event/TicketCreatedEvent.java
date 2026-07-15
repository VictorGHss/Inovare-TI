package br.dev.ctrls.inovareti.modules.ticket.domain.event;

import java.util.List;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;

/**
 * Domain event published when a new ticket is created.
 */
public record TicketCreatedEvent(Ticket ticket, List<User> assignedUsers) {
}
