package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordTicketService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    @Transactional
    public String createTicketFromDiscord(String discordUserId, String description, String priorityRaw) {
        User requester = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (requester == null) {
            return "⚠️ Seu Discord não está vinculado à sua conta da clínica. Use o comando /vincular [seu-email].";
        }

        TicketCategory category = ticketCategoryRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ticket category found in database"));

        TicketPriority priority = parsePriority(priorityRaw);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        Ticket ticket = Ticket.builder()
                .title("Support request from Discord")
                .description(description)
                .status(TicketStatus.OPEN)
                .priority(priority)
                .requester(requester)
                .category(category)
                .slaDeadline(now.plusHours(category.getBaseSlaHours()))
                .createdAt(now)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        String ticketIdShort = savedTicket.getId().toString().substring(0, 8).toUpperCase();

        return "✅ Chamado #" + ticketIdShort + " aberto com sucesso! A TI foi notificada.";
    }

    @Transactional(readOnly = true)
    public String getTicketStatusFromDiscord(String rawTicketId) {
        UUID ticketId;
        try {
            ticketId = UUID.fromString(rawTicketId);
        } catch (IllegalArgumentException ex) {
            return "❌ Invalid ticket ID format. Please provide a valid UUID.";
        }

        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return "❌ Ticket not found.";
        }

        String assignedTo = ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : "Unassigned";
        return "📋 Ticket #" + ticket.getId().toString().substring(0, 8).toUpperCase()
                + " | Status: " + ticket.getStatus().name()
                + " | Priority: " + ticket.getPriority().name()
                + " | Requester: " + ticket.getRequester().getName()
                + " | Assigned to: " + assignedTo;
    }

    private TicketPriority parsePriority(String priorityRaw) {
        if (priorityRaw == null || priorityRaw.isBlank()) {
            return TicketPriority.NORMAL;
        }
        try {
            return TicketPriority.valueOf(priorityRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid priority '{}'. Falling back to NORMAL.", priorityRaw);
            return TicketPriority.NORMAL;
        }
    }
}
