package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for sending real-time alerts to Discord via webhooks
 * when tickets are created.
 * 
 * This service uses @Async to prevent blocking the API while Discord processes
 * the webhook payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

    @Value("${discord.webhook.url:}")
    private String discordWebhookUrl;

    private final RestTemplate restTemplate;

    /**
     * Sends a new ticket alert to Discord asynchronously.
     * If the webhook URL is empty or null, logs a warning and aborts.
     *
     * @param ticket the ticket that was created
     */
    @Async
    public void sendNewTicketAlert(Ticket ticket) {
        // Log webhook URL configuration status
        log.info("Starting Discord webhook send. URL configured: {}", 
                discordWebhookUrl != null && !discordWebhookUrl.isBlank() ? "YES" : "NO");

        // Abort if webhook URL is not configured
        if (discordWebhookUrl == null || discordWebhookUrl.isBlank()) {
            log.warn("Discord webhook cancelled: URL not configured.");
            return;
        }

        try {
            String ticketIdShort = ticket.getId().toString()
                    .substring(0, 8)
                    .toUpperCase();

            Map<String, Object> payload = buildDiscordPayload(ticket, ticketIdShort);

            // Set up HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "InovareTI-Bot");

            // Create HTTP entity with payload and headers
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // Send the webhook
            restTemplate.postForObject(discordWebhookUrl, request, String.class);

            log.info("Discord webhook notification sent successfully for ticket #{}",
                    ticketIdShort);
        } catch (RestClientException e) {
            log.error("Error sending Discord webhook for ticket: {}", ticket.getId(), e);
        }
    }

    /**
     * Builds the Discord embed payload according to the Discord Webhooks API specification.
     * 
     * Payload structure:
     * {
     *   "embeds": [
     *     {
     *       "title": "...",
     *       "description": "...",
     *       "color": 16711680,
     *       "fields": [
     *         { "name": "...", "value": "...", "inline": true },
     *         ...
     *       ],
     *       "footer": { "text": "..." },
     *       "timestamp": 1234567890
     *     }
     *   ]
     * }
     *
     * @param ticket the ticket object containing notification data
     * @param ticketIdShort the short ticket ID for display
     * @return a Map representing the Discord webhook payload
     */
    private Map<String, Object> buildDiscordPayload(Ticket ticket, String ticketIdShort) {
        log.debug("Building Discord payload for ticket #{}", ticketIdShort);
        
        int embedColor = getEmbedColor(ticket.getPriority());
        String requesterName = ticket.getRequester().getName();
        String requesterSector = ticket.getRequester().getSector().getName();
        String priorityText = ticket.getPriority().toString();

        // Build the embed fields
        List<Map<String, Object>> fields = new ArrayList<>();

        // Requester info field
        fields.add(Map.of(
                "name", "Requester",
                "value", String.format("%s (%s)", requesterName, requesterSector),
                "inline", true
        ));

        // Priority field
        fields.add(Map.of(
                "name", "Priority",
                "value", priorityText,
                "inline", true
        ));

        // Category field
        fields.add(Map.of(
                "name", "Category",
                "value", ticket.getCategory().getName(),
                "inline", true
        ));

        // Build the embed object
        Map<String, Object> embed = Map.of(
                "title", String.format("🎫 New Ticket: #%s", ticketIdShort),
                "description", ticket.getTitle(),
                "color", embedColor,
                "fields", fields,
                "footer", Map.of(
                        "text", ticket.getId().toString()
                ),
                "timestamp", System.currentTimeMillis() / 1000
        );

        // Build the complete payload with embeds array
        Map<String, Object> payload = Map.of(
                "embeds", List.of(embed)
        );
        
        log.debug("Discord payload built successfully. Embed color: {}, Fields count: {}",
                embedColor, fields.size());
        
        return payload;
    }

    /**
     * Returns the Discord embed color in decimal format based on ticket priority.
     *
     * @param priority the ticket priority level
     * @return the color value in decimal format
     */
    private int getEmbedColor(TicketPriority priority) {
        return switch (priority) {
            case URGENT -> 0xFF0000;    // Red for URGENT
            case HIGH -> 0xFFAA00;      // Orange for HIGH
            case NORMAL -> 0x0099FF;    // Blue for NORMAL
            case LOW -> 0x00AA00;       // Green for LOW
        };
    }
}
