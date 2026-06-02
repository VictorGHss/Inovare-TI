package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.UpdateSolutionTextDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateSolutionTextUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId, UpdateSolutionTextDTO request) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        User currentUser = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado com id: " + userId));

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + ticketId));

        ticket.setSolutionText(request.solutionText() != null ? request.solutionText().trim() : null);

        Ticket savedTicket = ticketRepository.save(ticket);
        
        auditLogService.publish(AuditEvent.of(AuditAction.TICKET_RESOLVE)
            .userId(currentUser.getId())
            .resourceType("Ticket")
            .resourceId(savedTicket.getId())
            .details("{\"updatedSolutionText\": true}")
            .build());

        log.info("Solution text updated for ticket {} by user {} ({})", savedTicket.getId(), currentUser.getName(), currentUser.getEmail());

        return TicketResponseDTO.from(savedTicket);
    }
}
