package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de roteamento de notificações de chamados no Discord.
 *
 * Regras de distribuição:
 * 1) Chamado sem técnico responsável: notifica apenas ADMIN/TECHNICIAN com
 *    receives_it_notifications = true.
 * 2) Chamado assumido: notifica apenas o técnico responsável.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

     private final UserRepository userRepository;
     private final DiscordDirectMessageService discordDirectMessageService;

    @Async
    public void sendNewTicketAlert(Ticket ticket) {
        try {
            validateTicket(ticket);
            List<User> recipients = resolveRecipients(ticket);
            if (recipients.isEmpty()) {
                log.info("Discord notification skipped for ticket {}: no eligible recipients", ticket.getId());
                return;
            }

            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            String title = ticket.getAssignedTo() == null ? "Novo chamado aberto" : "Atualização de chamado";
            String description = ticket.getAssignedTo() == null
                    ? String.format("Chamado #%s aberto: %s", shortId, ticket.getTitle())
                    : String.format("Chamado #%s em acompanhamento: %s", shortId, ticket.getTitle());

            for (User recipient : recipients) {
                if (recipient.getDiscordUserId() == null || recipient.getDiscordUserId().isBlank()) {
                    continue;
                }
                discordDirectMessageService.sendTicketUpdateDMToUser(
                        recipient.getDiscordUserId(),
                        ticket.getId(),
                        title,
                        description);
            }

            log.info("Discord notifications queued for ticket {} to {} recipient(s)", ticket.getId(), recipients.size());
        } catch (IllegalArgumentException e) {
            UUID ticketId = ticket != null ? ticket.getId() : null;
            log.error("Validation error on Discord notification routing for ticket {}", ticketId, e);
        }
    }

    private List<User> resolveRecipients(Ticket ticket) {
        if (ticket.getAssignedTo() != null) {
            User assignee = ticket.getAssignedTo();
            if (!assignee.isReceivesItNotifications()) {
                return List.of();
            }
            return List.of(assignee);
        }

        UUID requesterId = ticket.getRequester().getId();

        return userRepository.findAllByRoleInAndReceivesItNotificationsTrue(
            List.of(UserRole.ADMIN, UserRole.TECHNICIAN))
            .stream()
            .filter(user -> !Objects.equals(user.getId(), requesterId))
            .toList();
    }

    /**
     * Valida o chamado e suas entidades relacionadas.
     *
     * @param ticket o chamado a ser validado
     * @throws IllegalArgumentException se a validação falhar
     */
    private void validateTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        if (ticket.getRequester() == null) {
            throw new IllegalArgumentException("Ticket requester cannot be null");
        }
        if (ticket.getPriority() == null) {
            throw new IllegalArgumentException("Ticket priority cannot be null");
        }
        if (ticket.getCategory() == null) {
            throw new IllegalArgumentException("Ticket category cannot be null");
        }
    }
}
