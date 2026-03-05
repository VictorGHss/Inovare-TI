package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.ArrayList;
import java.util.HashMap;
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
     * Uses explicit HashMap and ArrayList for proper JSON serialization by Jackson.
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
        
        // Validate ticket and related entities
        if (ticket == null) {
            log.error("Cannot build Discord payload: ticket is null");
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        if (ticket.getRequester() == null) {
            log.error("Cannot build Discord payload: ticket requester is null");
            throw new IllegalArgumentException("Ticket requester cannot be null");
        }
        if (ticket.getPriority() == null) {
            log.error("Cannot build Discord payload: ticket priority is null");
            throw new IllegalArgumentException("Ticket priority cannot be null");
        }
        if (ticket.getCategory() == null) {
            log.error("Cannot build Discord payload: ticket category is null");
            throw new IllegalArgumentException("Ticket category cannot be null");
        }
        
        Integer embedColor = getColorByPriority(ticket.getPriority().name());
        String requesterName = ticket.getRequester().getName();
        String requesterSector = ticket.getRequester().getSector() != null 
            ? ticket.getRequester().getSector().getName() 
            : "Unknown Sector";
        String priorityText = ticket.getPriority().name();
        String categoryName = ticket.getCategory().getName();

        log.debug("Building fields for ticket. Requester: {}, Priority: {}, Category: {}", 
                requesterName, priorityText, categoryName);

        // Build the embed fields using explicit HashMaps
        List<Map<String, Object>> fields = new ArrayList<>();

        // Requester info field
        Map<String, Object> field1 = new HashMap<>();
        field1.put("name", "Requester");
        field1.put("value", String.format("%s (%s)", requesterName, requesterSector));
        field1.put("inline", true);
        fields.add(field1);

        // Priority field
        Map<String, Object> field2 = new HashMap<>();
        field2.put("name", "Priority");
        field2.put("value", priorityText);
        field2.put("inline", true);
        fields.add(field2);

        // Category field
        Map<String, Object> field3 = new HashMap<>();
        field3.put("name", "Category");
        field3.put("value", categoryName);
        field3.put("inline", true);
        fields.add(field3);

        // Build the footer object
        Map<String, Object> footer = new HashMap<>();
        footer.put("text", ticket.getId().toString());

        // Build the embed object using explicit HashMap
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", String.format("🎫 New Ticket: #%s", ticketIdShort));
        embed.put("description", ticket.getTitle());
        embed.put("color", embedColor);
        embed.put("fields", fields);
        embed.put("footer", footer);
        embed.put("timestamp", System.currentTimeMillis() / 1000);

        // Build the complete payload with embeds array
        List<Map<String, Object>> embedsList = new ArrayList<>();
        embedsList.add(embed);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("embeds", embedsList);
        
        log.debug("Discord payload built successfully. Embed color: {}, Fields count: {}", 
                embedColor, fields.size());
        log.debug("Payload structure: embeds count={}, first embed title={}", 
                embedsList.size(), embed.get("title"));
        
        return payload;
    }

    /**
     * Returns the Discord embed color in decimal format based on ticket priority.
     *
     * @param priorityName the ticket priority name (e.g., "URGENT", "HIGH", "NORMAL", "LOW")
     * @return the color value in decimal format as Integer
     */
    private Integer getColorByPriority(String priorityName) {
        if (priorityName == null || priorityName.isBlank()) {
            log.warn("Priority name is null or blank, using default color");
            return 0x0099FF; // Default to NORMAL (Blue)
        }
        
        return switch (priorityName) {
            case "URGENT" -> 0xFF0000;    // Red for URGENT
            case "HIGH" -> 0xFFAA00;      // Orange for HIGH
            case "NORMAL" -> 0x0099FF;    // Blue for NORMAL
            case "LOW" -> 0x00AA00;       // Green for LOW
            default -> {
                log.warn("Unknown priority: {}, using default color", priorityName);
                yield 0x0099FF; // Default to NORMAL (Blue)
            }
        };
    }
}
