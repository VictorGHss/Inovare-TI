package br.dev.ctrls.inovareti.modules.ticket.domain.event;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;

/**
 * Domain event published when a ticket is resolved.
 */
public record TicketResolvedEvent(Ticket ticket) {
}
