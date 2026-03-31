package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import br.dev.ctrls.inovareti.domain.financeiro.SystemAlert;
import br.dev.ctrls.inovareti.domain.financeiro.SystemAlertRepository;
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
    private final RestTemplate restTemplate;
    private final SystemAlertRepository systemAlertRepository;

    @Value("${discord.operational.webhook.url:}")
    private String operationalWebhookUrl;

    @Value("${discord.webhook.url:}")
    private String defaultWebhookUrl;

    @Async
    public void sendNewTicketAlert(Ticket ticket) {
        try {
            validateTicket(ticket);
            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            String title = ticket.getAssignedTo() == null ? "Novo chamado aberto" : "Atualização de chamado";
            String description = ticket.getAssignedTo() == null
                    ? String.format("Chamado #%s aberto: %s", shortId, ticket.getTitle())
                    : String.format("Chamado #%s em acompanhamento: %s", shortId, ticket.getTitle());

            List<User> recipients = resolveRecipients(ticket);
            if (recipients.isEmpty()) {
                log.info("Notificação Discord ignorada para o chamado {}: nenhum destinatário elegível", ticket.getId());
                // Ainda assim, notifica o canal operacional para garantir visibilidade.
                sendOperationalAlert(title, description);
                return;
            }

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

            log.info("Notificações Discord enfileiradas para o chamado {} para {} destinatário(s)", ticket.getId(), recipients.size());

            // Sempre enviar uma notificação ao canal operacional para que a
            // equipe de operações receba um resumo do novo chamado.
            sendOperationalAlert(title, description);
        } catch (IllegalArgumentException e) {
            UUID ticketId = ticket != null ? ticket.getId() : null;
            log.error("Erro de validação no roteamento de notificação Discord para o chamado {}", ticketId, e);
        }
    }

    /**
     * Envia uma mensagem ao canal operacional (webhook) do Discord.
     * Método assíncrono e tolerante a falhas para não impactar o fluxo principal.
     */
    @Async
    public void sendOperationalAlert(String title, String message) {
        String webhook = StringUtils.hasText(operationalWebhookUrl) ? operationalWebhookUrl : defaultWebhookUrl;
        if (!StringUtils.hasText(webhook)) {
            log.warn("Operational Discord webhook not configured. Skipping operational alert: {}", title);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(webhook, new HttpEntity<>(Map.of("content", message), headers), Void.class);
            log.info("Operational alert enfileirada no Discord: {}", title);
        } catch (RestClientException ex) {
            log.error("Falha ao enviar alerta operacional no Discord: {}", title, ex);
            try {
                SystemAlert alert = SystemAlert.builder()
                        .alertType("DISCORD_OPERATIONAL_ALERT")
                        .severity("ERROR")
                        .source("DiscordWebhookService")
                        .title("Falha ao enviar alerta operacional no Discord: " + title)
                        .details(ex.getMessage())
                        .context(Map.of("webhook", webhook, "title", title))
                        .build();

                systemAlertRepository.save(alert);
            } catch (Exception e) {
                log.warn("Falha ao registrar SystemAlert após falha no webhook do Discord: {}", e.getMessage(), e);
            }
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
            throw new IllegalArgumentException("Chamado não pode ser nulo");
        }
        if (ticket.getRequester() == null) {
            throw new IllegalArgumentException("Solicitante do chamado não pode ser nulo");
        }
        if (ticket.getPriority() == null) {
            throw new IllegalArgumentException("Prioridade do chamado não pode ser nula");
        }
        if (ticket.getCategory() == null) {
            throw new IllegalArgumentException("Categoria do chamado não pode ser nula");
        }
    }
}
