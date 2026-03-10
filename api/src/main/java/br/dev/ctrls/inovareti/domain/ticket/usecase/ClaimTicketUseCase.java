package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
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
public class ClaimTicketUseCase {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final DiscordDirectMessageService discordDirectMessageService;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Authenticated user not found with id: " + userId));

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));

        ticket.setAssignedTo(currentUser);
        ticket.setStatus(TicketStatus.IN_PROGRESS);

        Ticket savedTicket = ticketRepository.save(ticket);

        String shortId = savedTicket.getId().toString().substring(0, 8).toUpperCase();
        String dmTitle = "Chamado Assumido";
        String dmDescription = "Seu chamado #" + shortId
            + " foi assumido pelo técnico **" + currentUser.getName() + "**.";
        discordDirectMessageService.sendTicketUpdateDM(savedTicket, dmTitle, dmDescription);

        log.info("Ticket {} claimed by user {} ({})", savedTicket.getId(), currentUser.getName(), currentUser.getEmail());

        return TicketResponseDTO.from(savedTicket);
    }
}
