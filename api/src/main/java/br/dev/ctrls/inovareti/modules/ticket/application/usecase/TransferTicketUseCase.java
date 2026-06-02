package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.notification.application.service.CreateNotificationService;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferTicketUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;
    private final CreateNotificationService createNotificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId, UUID newUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + ticketId));

        User newAssignee = userRepository.findById(newUserId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado com id: " + newUserId));

        ticket.setAssignedTo(newAssignee);

        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.publish(AuditEvent.of(AuditAction.TICKET_TRANSFER)
            .userId(newAssignee.getId())
            .resourceType("Ticket")
            .resourceId(savedTicket.getId())
            .details("{\"newAssignee\": \"" + newAssignee.getEmail() + "\"}")
            .build());
        // Notifica o novo responsável sobre a transferência
        createNotificationService.create(
                newAssignee.getId(),
                "Chamado Transferido",
                "Um chamado #" + savedTicket.getId().toString().substring(0, 8) + " foi transferido para você",
                "/tickets/" + savedTicket.getId()
        );

        
        log.info("Chamado {} transferido para o usuário {} ({})", savedTicket.getId(), newAssignee.getName(), newAssignee.getEmail());

        return TicketResponseDTO.from(savedTicket);
    }
}
