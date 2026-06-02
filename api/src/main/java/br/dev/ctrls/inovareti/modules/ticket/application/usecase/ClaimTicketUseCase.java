package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
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
public class ClaimTicketUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;
    private final DiscordDirectMessageService discordDirectMessageService;
    private final AuditLogService auditLogService;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        User currentUser = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado com id: " + userId));

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + ticketId));

        ticket.setAssignedTo(currentUser);
        ticket.setStatus(TicketStatus.IN_PROGRESS);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.publish(AuditEvent.of(AuditAction.TICKET_ASSIGN)
            .userId(currentUser.getId())
            .resourceType("Ticket")
            .resourceId(savedTicket.getId())
            .details("{\"assignedTo\": \"" + currentUser.getName() + "\"}")
            .build());

        String shortId = savedTicket.getId().toString().substring(0, 8).toUpperCase();
        String dmTitle = "Chamado Assumido";
        String dmDescription = "Seu chamado #" + shortId
            + " foi assumido pelo técnico **" + currentUser.getName() + "**.";
        discordDirectMessageService.sendTicketUpdateDM(savedTicket, dmTitle, dmDescription);

        log.info("Chamado {} assumido pelo usuário {} ({})", savedTicket.getId(), currentUser.getName(), currentUser.getEmail());

        return TicketResponseDTO.from(savedTicket);
    }
}
