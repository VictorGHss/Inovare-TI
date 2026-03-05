package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for sending real-time alerts to Discord via webhooks
 * when tickets are created.
 * 
 * This service uses @Async to prevent blocking the API while Discord processes
 * the webhook payload.
 * 
 * Uses strongly-typed Java Records to ensure proper JSON serialization.
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
            // Validate ticket and related entities
            validateTicket(ticket);

            String ticketIdShort = ticket.getId().toString()
                    .substring(0, 8)
                    .toUpperCase();

            log.debug("Building Discord payload for ticket #{}", ticketIdShort);

            // Extract data with null safety
            String requesterName = ticket.getRequester().getName();
            String requesterSector = ticket.getRequester().getSector() != null 
                ? ticket.getRequester().getSector().getName() 
                : "Unknown Sector";
            String priorityName = ticket.getPriority().name();
            String categoryName = ticket.getCategory().getName();

            // Build fields with strict typing
            var field1 = new DiscordField("Solicitante", requesterName, true);
            var field2 = new DiscordField("Setor", requesterSector, true);
            var field3 = new DiscordField("Prioridade", priorityName, true);
            var field4 = new DiscordField("Categoria", categoryName, true);

            // Build embed with strict typing
            Integer embedColor = getColorByPriority(priorityName);
            var embed = new DiscordEmbed(
                String.format("🎫 Novo Chamado: #%s", ticketIdShort),
                ticket.getTitle(),
                embedColor,
                List.of(field1, field2, field3, field4)
            );

            // Build payload with strict typing
            var payload = new DiscordPayload(List.of(embed));

            log.debug("Payload built successfully. Embed color: {}, Fields count: {}", 
                    embedColor, 4);

            // Set up HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "InovareTI-Bot");

            // Create HTTP entity with typed payload
            HttpEntity<DiscordPayload> request = new HttpEntity<>(payload, headers);

            // Send the webhook
            restTemplate.postForObject(discordWebhookUrl, request, String.class);

            log.info("Discord webhook notification sent successfully for ticket #{}",
                    ticketIdShort);
        } catch (IllegalArgumentException e) {
            log.error("Validation error building Discord webhook for ticket: {}", 
                    ticket.getId(), e);
        } catch (RestClientException e) {
            log.error("Error sending Discord webhook for ticket: {}", ticket.getId(), e);
        }
    }

    /**
     * Validates ticket and related entities.
     *
     * @param ticket the ticket to validate
     * @throws IllegalArgumentException if validation fails
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

    /**
     * Returns the Discord embed color in decimal format based on ticket priority.
     * Colors are returned as strict Integer values.
     *
     * @param priorityName the ticket priority name (e.g., "URGENT", "HIGH", "NORMAL", "LOW")
     * @return the color value in decimal format as Integer
     */
    private Integer getColorByPriority(String priorityName) {
        if (priorityName == null || priorityName.isBlank()) {
            log.warn("Priority name is null or blank, using default color");
            return 39423; // Default to NORMAL (Blue)
        }
        
        return switch (priorityName) {
            case "URGENT" -> 16711680;    // Red for URGENT
            case "HIGH" -> 16753920;      // Orange for HIGH
            case "NORMAL" -> 39423;       // Blue for NORMAL
            case "LOW" -> 43520;          // Green for LOW
            default -> {
                log.warn("Unknown priority: {}, using default color", priorityName);
                yield 39423; // Default to NORMAL (Blue)
            }
        };
    }

    /**
     * Discord Field Record - strictly typed field for embeds.
     */
    public record DiscordField(String name, String value, boolean inline) {}

    /**
     * Discord Embed Record - strictly typed embed object.
     */
    public record DiscordEmbed(
        String title,
        String description,
        Integer color,
        List<DiscordField> fields
    ) {}

    /**
     * Discord Payload Record - strictly typed webhook payload.
     * This is the root object sent to Discord Webhooks API.
     */
    public record DiscordPayload(List<DiscordEmbed> embeds) {}
}
