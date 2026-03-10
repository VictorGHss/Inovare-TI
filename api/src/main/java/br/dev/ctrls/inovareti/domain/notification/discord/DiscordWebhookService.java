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
 * Serviço responsável por enviar alertas em tempo real ao Discord via webhooks
 * quando chamados são criados.
 *
 * Utiliza @Async para não bloquear a API enquanto o Discord processa o payload.
 *
 * Usa Java Records com tipagem estrita para garantir a serialização JSON correta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

    @Value("${discord.webhook.url:}")
    private String discordWebhookUrl;

    private final RestTemplate restTemplate;

    /**
     * Envia um alerta de novo chamado ao Discord de forma assíncrona.
     * Se a URL do webhook estiver vazia ou nula, registra um aviso e aborta.
     *
     * @param ticket o chamado que foi criado
     */
    @Async
    public void sendNewTicketAlert(Ticket ticket) {
        // Registra o status de configuração da URL do webhook
        log.info("Starting Discord webhook send. URL configured: {}", 
                discordWebhookUrl != null && !discordWebhookUrl.isBlank() ? "YES" : "NO");

        // Aborta se a URL do webhook não estiver configurada
        if (discordWebhookUrl == null || discordWebhookUrl.isBlank()) {
            log.warn("Discord webhook cancelled: URL not configured.");
            return;
        }

        try {
            // Valida o chamado e entidades relacionadas
            validateTicket(ticket);

            String ticketIdShort = ticket.getId().toString()
                    .substring(0, 8)
                    .toUpperCase();

            log.debug("Building Discord payload for ticket #{}", ticketIdShort);

            // Extrai dados com proteção contra nulos
            String requesterName = ticket.getRequester().getName();
            String requesterSector = ticket.getRequester().getSector() != null 
                ? ticket.getRequester().getSector().getName() 
                : "Unknown Sector";
            String priorityName = ticket.getPriority().name();
            String categoryName = ticket.getCategory().getName();

            // Constrói os fields com tipagem estrita
            var field1 = new DiscordField("Solicitante", requesterName, true);
            var field2 = new DiscordField("Setor", requesterSector, true);
            var field3 = new DiscordField("Prioridade", priorityName, true);
            var field4 = new DiscordField("Categoria", categoryName, true);

            // Constrói o embed com tipagem estrita
            Integer embedColor = getColorByPriority(priorityName);
            var embed = new DiscordEmbed(
                String.format("🎫 Novo Chamado: #%s", ticketIdShort),
                ticket.getTitle(),
                embedColor,
                List.of(field1, field2, field3, field4)
            );

            // Constrói o payload com tipagem estrita
            var payload = new DiscordPayload(List.of(embed));

            log.debug("Payload built successfully. Embed color: {}, Fields count: {}", 
                    embedColor, 4);

            // Configura os cabeçalhos HTTP
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "InovareTI-Bot");

            // Cria a entidade HTTP com o payload tipado
            HttpEntity<DiscordPayload> request = new HttpEntity<>(payload, headers);

            // Envia o webhook
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

    /**
     * Retorna a cor do embed do Discord em formato decimal com base na prioridade do chamado.
     * As cores são retornadas como valores Integer estritos.
     *
     * @param priorityName o nome da prioridade do chamado (ex.: "URGENT", "HIGH", "NORMAL", "LOW")
     * @return o valor da cor em formato decimal como Integer
     */
    private Integer getColorByPriority(String priorityName) {
        if (priorityName == null || priorityName.isBlank()) {
            log.warn("Priority name is null or blank, using default color");
            return 39423; // Padrão: NORMAL (Azul)
        }
        
        return switch (priorityName) {
            case "URGENT" -> 16711680;    // Vermelho para URGENTE
            case "HIGH" -> 16753920;      // Laranja para ALTA
            case "NORMAL" -> 39423;       // Azul para NORMAL
            case "LOW" -> 43520;          // Verde para BAIXA
            default -> {
                log.warn("Unknown priority: {}, using default color", priorityName);
                yield 39423; // Padrão: NORMAL (Azul)
            }
        };
    }

    /**
     * Record DiscordField — field com tipagem estrita para embeds.
     */
    public record DiscordField(String name, String value, boolean inline) {}

    /**
     * Record DiscordEmbed — objeto embed com tipagem estrita.
     */
    public record DiscordEmbed(
        String title,
        String description,
        Integer color,
        List<DiscordField> fields
    ) {}

    /**
     * Record DiscordPayload — payload do webhook com tipagem estrita.
     * É o objeto raiz enviado à API de Webhooks do Discord.
     */
    public record DiscordPayload(List<DiscordEmbed> embeds) {}
}
