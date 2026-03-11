package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService;
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
    private final DiscordWebhookService discordWebhookService;

    @Transactional
    public String createTicketFromDiscord(String discordUserId, String description, String priorityRaw) {
        User requester = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (requester == null) {
            return "⚠️ Seu Discord não está vinculado à sua conta da clínica. Use o comando /vincular [seu-email].";
        }

        TicketCategory category = ticketCategoryRepository.findAll().stream()
                .findFirst()
            .orElseThrow(() -> new IllegalStateException("Nenhuma categoria de chamado foi encontrada no banco de dados"));

        TicketPriority priority = parsePriority(priorityRaw);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        String normalizedDescription = description.trim();
        String title = normalizedDescription.length() > 40
            ? normalizedDescription.substring(0, 37) + "..."
            : normalizedDescription;
        String storedDescription = "[DISCORD] " + normalizedDescription;

        Ticket ticket = Ticket.builder()
            .title(title)
                .description(storedDescription)
                .status(TicketStatus.OPEN)
                .priority(priority)
                .requester(requester)
                .category(category)
                .slaDeadline(now.plusHours(category.getBaseSlaHours()))
                .createdAt(now)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        initializeWebhookRelations(savedTicket);
        discordWebhookService.sendNewTicketAlert(savedTicket);

        String ticketIdShort = savedTicket.getId().toString().substring(0, 8).toUpperCase();

        return "✅ Chamado #" + ticketIdShort + " aberto com sucesso! A TI foi notificada.";
    }

    @Transactional(readOnly = true)
    public String getTicketStatusFromDiscord(String rawTicketId) {
        Ticket ticket = findTicketByIdentifier(rawTicketId);
        if (ticket == null) {
            return "❌ Chamado não encontrado.";
        }

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        return String.format(
                "🔍 O chamado #%s está atualmente: **%s**",
                shortId,
                toFriendlyStatus(ticket.getStatus())
        );
    }

    @Transactional(readOnly = true)
    public String listMyActiveTicketsFromDiscord(String discordUserId) {
        User requester = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (requester == null) {
            return "⚠️ Sua conta não está vinculada! Digite o comando /vincular e informe seu e-mail corporativo. Depois, é só usar o /meuschamados novamente!";
        }

        List<TicketStatus> activeStatuses = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        List<Ticket> activeTickets = ticketRepository.findByRequesterIdAndStatusInOrderByCreatedAtDesc(
                requester.getId(),
                activeStatuses
        );

        if (activeTickets.isEmpty()) {
            return "Você não tem nenhum chamado em andamento no momento! 🎉";
        }

        StringBuilder messageBuilder = new StringBuilder("Seus chamados em andamento:\n");
        for (Ticket ticket : activeTickets) {
            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            messageBuilder
                    .append("📌 #")
                    .append(shortId)
                    .append(" - ")
                    .append(ticket.getTitle())
                    .append(" (Status: ")
                    .append(toFriendlyStatus(ticket.getStatus()))
                    .append(")\n");
        }

        return messageBuilder.toString().trim();
    }

    private TicketPriority parsePriority(String priorityRaw) {
        if (priorityRaw == null || priorityRaw.isBlank()) {
            return TicketPriority.NORMAL;
        }
        try {
            return TicketPriority.valueOf(priorityRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Prioridade '{}' inválida. Aplicando NORMAL como padrão.", priorityRaw);
            return TicketPriority.NORMAL;
        }
    }

    private Ticket findTicketByIdentifier(String rawTicketId) {
        if (rawTicketId == null || rawTicketId.isBlank()) {
            return null;
        }

        if (rawTicketId.length() < 36) {
            return ticketRepository.findByShortIdStartingWith(rawTicketId)
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        try {
            UUID ticketId = UUID.fromString(rawTicketId);
            return ticketRepository.findById(ticketId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String toFriendlyStatus(TicketStatus status) {
        return switch (status) {
            case OPEN -> "ABERTO";
            case IN_PROGRESS -> "EM ANDAMENTO";
            case RESOLVED -> "RESOLVIDO";
        };
    }

    private void initializeWebhookRelations(Ticket ticket) {
        ticket.getRequester().getName();
        ticket.getRequester().getSector().getName();
        ticket.getCategory().getName();
    }
}
