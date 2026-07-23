package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.DiscordTicketPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsável por vincular um chamado filho a um chamado pai.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkTicketUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final DiscordTicketPort discordTicketPort;

    @Transactional
    public TicketResponseDTO execute(UUID childId, UUID parentTicketId) {
        Ticket childTicket = ticketRepository.findById(childId)
                .orElseThrow(() -> new NotFoundException("Chamado filho não encontrado com id: " + childId));

        Ticket parentTicket = ticketRepository.findById(parentTicketId)
                .orElseThrow(() -> new NotFoundException("Chamado pai não encontrado com id: " + parentTicketId));

        childTicket.setParentTicketId(parentTicketId);

        if (childTicket.getRelatedTickets() == null) {
            childTicket.setRelatedTickets(new java.util.HashSet<>());
        }
        if (parentTicket.getRelatedTickets() == null) {
            parentTicket.setRelatedTickets(new java.util.HashSet<>());
        }

        childTicket.getRelatedTickets().add(parentTicket);
        parentTicket.getRelatedTickets().add(childTicket);

        Ticket savedChild = ticketRepository.save(childTicket);
        ticketRepository.save(parentTicket);

        log.info("[LINK-TICKET] Chamado #{} (filho) unificado ao Chamado Mestre #{} (pai)",
                savedChild.getNumber(), parentTicket.getNumber());

        // Publica notificação no Discord
        try {
            discordTicketPort.notifyMerged(savedChild, parentTicket);
        } catch (Exception ex) {
            log.error("[LINK-TICKET] Falha ao notificar unificação no Discord para o chamado #{}", savedChild.getNumber(), ex);
        }

        return TicketResponseDTO.from(savedChild);
    }
}
