package br.dev.ctrls.inovareti.domain.ticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;

/**
 * Scheduler de alertas de SLA crítico para chamados em andamento.
 *
 * <p>Executa a cada 15 minutos e verifica chamados com {@code slaDeadline} expirando
 * em menos de 30 minutos. Para cada chamado crítico encontrado, envia uma
 * <b>DM privada</b> diretamente para o Discord do técnico responsável com um
 * embed vermelho de urgência.</p>
 *
 * <p>Se o chamado não tiver técnico atribuído ou o técnico não tiver Discord
 * vinculado, o alerta é registrado em log como aviso.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaAlertScheduler {

    private static final int SLA_ALERT_THRESHOLD_MINUTES = 30;
    private static final int EMBED_COLOR_RED = 0xE53E3E;

    private final TicketRepository ticketRepository;
    private final ObjectProvider<JDA> jdaProvider;

    /**
     * Roda a cada 15 minutos e envia DMs de alerta vermelho para os técnicos
     * cujos chamados estão prestes a estourar o SLA.
     */
    @Scheduled(fixedDelay = 900_000)
    @Transactional(readOnly = true)
    public void checkSlaExpirations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusMinutes(SLA_ALERT_THRESHOLD_MINUTES);

        List<Ticket> criticalTickets = ticketRepository.findAllByStatus(TicketStatus.OPEN)
                .stream()
                .filter(t -> t.getSlaDeadline() != null
                        && t.getSlaDeadline().isAfter(now)
                        && t.getSlaDeadline().isBefore(threshold))
                .toList();

        // Também verifica IN_PROGRESS
        List<Ticket> inProgressCritical = ticketRepository.findAllByStatus(TicketStatus.IN_PROGRESS)
                .stream()
                .filter(t -> t.getSlaDeadline() != null
                        && t.getSlaDeadline().isAfter(now)
                        && t.getSlaDeadline().isBefore(threshold))
                .toList();

        List<Ticket> allCritical = new java.util.ArrayList<>(criticalTickets);
        allCritical.addAll(inProgressCritical);

        if (allCritical.isEmpty()) {
            log.debug("[SLA-SCHEDULER] Nenhum chamado crítico encontrado (janela: {} -> {})", now, threshold);
            return;
        }

        log.warn("[SLA-SCHEDULER] {} chamado(s) com SLA crítico encontrado(s)!", allCritical.size());

        JDA jda = jdaProvider.getIfAvailable();

        for (Ticket ticket : allCritical) {
            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            long minutesRemaining = java.time.Duration.between(now, ticket.getSlaDeadline()).toMinutes();

            log.warn("[SLA-SCHEDULER] ⚠️ Chamado #{} expira em {} minutos. Técnico: {}",
                    shortId, minutesRemaining,
                    ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : "Não atribuído");

            if (ticket.getAssignedTo() == null) {
                log.warn("[SLA-SCHEDULER] Chamado #{} sem técnico atribuído — alerta de SLA não pode ser enviado.", shortId);
                continue;
            }

            String technicianDiscordId = ticket.getAssignedTo().getDiscordUserId();
            if (technicianDiscordId == null || technicianDiscordId.isBlank()) {
                log.warn("[SLA-SCHEDULER] Técnico '{}' do chamado #{} não possui Discord vinculado — DM ignorada.",
                        ticket.getAssignedTo().getName(), shortId);
                continue;
            }

            if (jda == null) {
                log.warn("[SLA-SCHEDULER] JDA não disponível — DM de alerta SLA para chamado #{} não enviada.", shortId);
                continue;
            }

            sendSlaAlertDM(jda, technicianDiscordId, ticket, shortId, minutesRemaining);
        }
    }

    private void sendSlaAlertDM(JDA jda, String discordUserId, Ticket ticket, String shortId, long minutesRemaining) {
        String requesterName = ticket.getRequester() != null ? ticket.getRequester().getName() : "Desconhecido";
        String categoryName = ticket.getCategory() != null ? ticket.getCategory().getName() : "Não categorizado";

        var embed = new EmbedBuilder()
                .setColor(EMBED_COLOR_RED)
                .setTitle("🚨 ALERTA DE SLA CRÍTICO — Chamado #" + shortId)
                .setDescription(
                        "**Atenção, " + ticket.getAssignedTo().getName() + "!**\n\n"
                        + "O chamado sob sua responsabilidade está prestes a estourar o SLA:\n\n"
                        + "📋 **Título:** " + ticket.getTitle() + "\n"
                        + "🏷️ **Categoria:** " + categoryName + "\n"
                        + "👤 **Solicitante:** " + requesterName + "\n"
                        + "⏱️ **SLA expira em:** " + minutesRemaining + " minutos\n\n"
                        + "⚡ Resolva ou escale o chamado imediatamente!")
                .setFooter("Inovare TI • Sistema de Monitoramento de SLA")
                .setTimestamp(java.time.Instant.now())
                .build();

        jda.retrieveUserById(Objects.requireNonNull(discordUserId)).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessageEmbeds(embed).queue(
                                success -> log.info("[SLA-SCHEDULER] Alerta de SLA enviado via DM para técnico '{}' (chamado #{})",
                                        ticket.getAssignedTo().getName(), shortId),
                                error -> log.warn("[SLA-SCHEDULER] Falha ao enviar DM de alerta SLA para '{}': {}",
                                        discordUserId, error.getMessage())
                        ),
                        error -> log.warn("[SLA-SCHEDULER] Falha ao abrir canal DM para '{}': {}", discordUserId, error.getMessage())
                ),
                error -> log.warn("[SLA-SCHEDULER] Falha ao recuperar usuário Discord '{}': {}", discordUserId, error.getMessage())
        );
    }
}
