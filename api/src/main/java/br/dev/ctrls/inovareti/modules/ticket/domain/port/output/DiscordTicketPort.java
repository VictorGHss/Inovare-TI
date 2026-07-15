package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;

import java.util.List;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;

/**
 * Output port for Discord operations regarding tickets.
 */
public interface DiscordTicketPort {

    /**
     * Creates a private and temporary channel in Discord for the given ticket,
     * adding the assigned users to it.
     *
     * @param ticket         the ticket that was created
     * @param discordUserIds the list of Discord user IDs to be added to the channel
     */
    void createTicketChannel(Ticket ticket, List<String> discordUserIds);

    /**
     * Archives the Discord channel associated with the given ticket.
     *
     * @param ticket the ticket that was resolved
     */
    void archiveTicketChannel(Ticket ticket);

    /**
     * Publishes a merge notification to the Discord channel of the child ticket.
     */
    void notifyMerged(Ticket childTicket, Ticket parentTicket);
}
