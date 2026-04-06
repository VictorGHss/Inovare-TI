package br.dev.ctrls.inovareti.domain.notification.discord;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;

/**
 * Builder responsável por montar payloads de embed para o Discord.
 *
 * Regras de apresentação (cor, campos, títulos e timestamps) ficam centralizadas
 * aqui para manter o serviço de webhook focado apenas em envio.
 */
@Component
public class DiscordEmbedBuilder {

    private static final int OPERATIONS_COLOR = 16692588;
    private static final DateTimeFormatter BR_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public Map<String, Object> buildOperationalAlertEmbed(String title, String message, String thumbnailUrl) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", StringUtils.hasText(title) ? title : "Alerta Operacional");
        embed.put("description", StringUtils.hasText(message) ? message : "");
        embed.put("color", OPERATIONS_COLOR);
        embed.put("timestamp", OffsetDateTime.now().toString());
        embed.put("footer", Map.of("text", "Gerado em: " + BR_DATE_TIME.format(OffsetDateTime.now())));

        if (StringUtils.hasText(thumbnailUrl)) {
            embed.put("thumbnail", Map.of("url", thumbnailUrl));
        }

        return embed;
    }

    public Map<String, Object> buildNewTicketEmbed(Ticket ticket, String thumbnailUrl, String ticketUrlBase) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "🚨 Novo Chamado Aberto");
        embed.put("color", OPERATIONS_COLOR);
        embed.put("timestamp", OffsetDateTime.now().toString());

        String description = ticket != null && StringUtils.hasText(ticket.getTitle()) ? ticket.getTitle() : "-";
        embed.put("description", description);

        UUID ticketId = ticket != null ? ticket.getId() : null;
        if (ticketId != null && StringUtils.hasText(ticketUrlBase)) {
            embed.put("url", ticketUrlBase + ticketId);
        }

        String shortId = ticketId != null ? ticketId.toString().substring(0, 8).toUpperCase() : "-";
        String requester = ticket != null && ticket.getRequester() != null ? ticket.getRequester().getName() : "-";
        String sector = ticket != null && ticket.getRequester() != null && ticket.getRequester().getSector() != null
                ? ticket.getRequester().getSector().getName()
                : "-";
        String priority = ticket != null && ticket.getPriority() != null ? ticket.getPriority().toString() : "-";

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("name", "ID", "value", shortId, "inline", true));
        fields.add(Map.of("name", "Solicitante", "value", requester, "inline", true));
        fields.add(Map.of("name", "Setor", "value", sector, "inline", true));
        fields.add(Map.of("name", "Prioridade", "value", priority, "inline", true));
        embed.put("fields", fields);

        if (StringUtils.hasText(thumbnailUrl)) {
            embed.put("thumbnail", Map.of("url", thumbnailUrl));
        }

        String openedAt = ticket != null && ticket.getCreatedAt() != null
                ? ticket.getCreatedAt().format(BR_DATE_TIME)
                : "-";
        embed.put("footer", Map.of("text", "Aberto em: " + openedAt));

        return embed;
    }
}
