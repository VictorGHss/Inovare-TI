package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferTicketUseCase {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CreateNotificationService createNotificationService;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId, UUID newUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));

        User newAssignee = userRepository.findById(newUserId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + newUserId));

        ticket.setAssignedTo(newAssignee);

        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        // Notify new assignee about the transfer
        createNotificationService.create(
                newAssignee.getId(),
                "Chamado Transferido",
                "Um chamado #" + savedTicket.getId().toString().substring(0, 8) + " foi transferido para você",
                "/tickets/" + savedTicket.getId()
        );

        
        log.info("Ticket {} transferred to user {} ({})", savedTicket.getId(), newAssignee.getName(), newAssignee.getEmail());

        return TicketResponseDTO.from(savedTicket);
    }
}
