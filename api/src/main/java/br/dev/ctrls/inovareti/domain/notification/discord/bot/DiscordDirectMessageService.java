package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordDirectMessageService {

    private static final int CLINIC_BRAND_COLOR = 0xF97316;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final ObjectProvider<JDA> jdaProvider;

    @Async
    public void sendTicketUpdateDM(Ticket ticket, String title, String description) {
        if (ticket == null || ticket.getRequester() == null) {
            log.warn("Skipping Discord DM: ticket or requester is null");
            return;
        }

        String discordUserId = ticket.getRequester().getDiscordUserId();
        if (discordUserId == null || discordUserId.isBlank()) {
            log.info("Skipping Discord DM for ticket {}: requester has no Discord user id", ticket.getId());
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("Skipping Discord DM for ticket {}: JDA is not available", ticket.getId());
            return;
        }

        String ticketUrl = buildTicketUrl(ticket);
        String content = description + "\n\n[Ver Chamado](" + ticketUrl + ")";

        var embed = new EmbedBuilder()
                .setColor(CLINIC_BRAND_COLOR)
                .setTitle(title)
                .setDescription(content)
                .build();

        jda.retrieveUserById(discordUserId).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessageEmbeds(embed).queue(
                                success -> log.info("Discord DM sent for ticket {} to user {}", ticket.getId(), discordUserId),
                                error -> log.warn("Failed to send Discord DM message for ticket {} to user {}", ticket.getId(), discordUserId, error)
                        ),
                        error -> log.warn("Failed to open Discord DM channel for ticket {} to user {}", ticket.getId(), discordUserId, error)
                ),
                error -> log.warn("Failed to retrieve Discord user {} for ticket {}", discordUserId, ticket.getId(), error)
        );
    }

    private String buildTicketUrl(Ticket ticket) {
        String normalizedBaseUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:5173";
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        return normalizedBaseUrl + "/tickets/" + ticket.getId();
    }
}
