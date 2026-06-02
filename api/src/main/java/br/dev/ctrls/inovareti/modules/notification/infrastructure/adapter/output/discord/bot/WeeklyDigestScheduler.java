package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.DiscordWebhookService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyDigestScheduler {

    @Value("${discord.bot.operational-channel-id:}")
    private String operationalChannelId;

    private final ObjectProvider<JDA> jdaProvider;
    private final DiscordWebhookService discordWebhookService;

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Roda todas as sextas-feiras às 17:00 e envia o resumo de métricas executivo para a equipe de TI.
     */
    @Scheduled(cron = "0 0 17 * * FRI")
    @Transactional(readOnly = true)
    @SuppressWarnings({"unchecked", "null"})
    public void sendWeeklyDigest() {
        log.info("[DIGEST] Iniciando compilação do relatório semanal de métricas de TI...");

        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime startOfWeek = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            java.time.LocalDateTime endOfWeek = now;

            // 1. Total Concluídos (RESOLVED)
            long closedCount = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM tickets WHERE status = 'RESOLVED' AND closed_at >= :start AND closed_at <= :end")
                    .setParameter("start", startOfWeek)
                    .setParameter("end", endOfWeek)
                    .getSingleResult()).longValue();

            // 2. Conformidade de SLA
            long slaMetCount = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM tickets WHERE status = 'RESOLVED' AND closed_at >= :start AND closed_at <= :end AND closed_at <= sla_deadline")
                    .setParameter("start", startOfWeek)
                    .setParameter("end", endOfWeek)
                    .getSingleResult()).longValue();

            double slaCompliance = closedCount > 0 ? (slaMetCount * 100.0) / closedCount : 100.0;

            // 3. Tag Gargalo (Mais Ativa)
            List<Object[]> activeTags = entityManager.createNativeQuery(
                    "SELECT tg.name, COUNT(t.id) as qty " +
                    "FROM tickets t " +
                    "JOIN ticket_tag_relations ttr ON t.id = ttr.ticket_id " +
                    "JOIN ticket_tags tg ON ttr.tag_id = tg.id " +
                    "WHERE t.created_at >= :start AND t.created_at <= :end " +
                    "GROUP BY tg.name " +
                    "ORDER BY qty DESC " +
                    "LIMIT 1")
                    .setParameter("start", startOfWeek)
                    .setParameter("end", endOfWeek)
                    .getResultList();

            String tagGargalo = activeTags.isEmpty() ? "Nenhuma tag ativa" : (String) activeTags.get(0)[0];
            long tagGargaloQty = activeTags.isEmpty() ? 0 : ((Number) activeTags.get(0)[1]).longValue();

            // 4. Setor Mais Impactado
            List<Object[]> sectorImpact = entityManager.createNativeQuery(
                    "SELECT s.name, COUNT(t.id) as qty " +
                    "FROM tickets t " +
                    "JOIN users u ON t.requester_id = u.id " +
                    "JOIN sectors s ON u.sector_id = s.id " +
                    "WHERE t.created_at >= :start AND t.created_at <= :end " +
                    "GROUP BY s.name " +
                    "ORDER BY qty DESC " +
                    "LIMIT 1")
                    .setParameter("start", startOfWeek)
                    .setParameter("end", endOfWeek)
                    .getResultList();

            String sectorMostImpacted = sectorImpact.isEmpty() ? "Nenhum setor impactado" : (String) sectorImpact.get(0)[0];
            long sectorMostImpactedQty = sectorImpact.isEmpty() ? 0 : ((Number) sectorImpact.get(0)[1]).longValue();

            // Monta o Embed do JDA
            var embed = new EmbedBuilder()
                    .setColor(0x1F85DE) // Azul sóbrio executivo
                    .setTitle("📊 INOVARE TI — RELATÓRIO EXECUTIVO SEMANAL")
                    .setDescription("Consolidado semanal de métricas de chamados e performance operacional da clínica.")
                    .addField("✅ Concluídos na Semana", closedCount + " chamado(s)", true)
                    .addField("⏱️ Conformidade de SLA", String.format("%.1f%%", slaCompliance), true)
                    .addField("🏷️ Tag Gargalo (Mais Ativa)", tagGargalo + " (" + tagGargaloQty + " chamados)", false)
                    .addField("🏢 Setor Mais Impactado", sectorMostImpacted + " (" + sectorMostImpactedQty + " chamados)", false)
                    .setFooter("Relatório Consolidado • Sexta-feira às 17:00")
                    .setTimestamp(java.time.Instant.now())
                    .build();

            JDA jda = jdaProvider.getIfAvailable();
            if (jda != null && operationalChannelId != null && !operationalChannelId.isBlank()) {
                TextChannel canal = jda.getTextChannelById(operationalChannelId.trim());
                if (canal != null) {
                    canal.sendMessageEmbeds(embed).queue(
                            ok -> log.info("[DIGEST] Relatório executivo semanal enviado para o canal '{}'", operationalChannelId),
                            err -> log.warn("[DIGEST] Falha ao enviar embed via JDA, enviando por webhook: {}", err.getMessage())
                    );
                    return;
                }
            }

            // Fallback via webhook se JDA não disponível
            String msgText = """
                    **📊 Relatório Semanal de TI:**
                    - **Total Concluído:** %d chamados
                    - **Conformidade SLA:** %.1f%%
                    - **Tag Gargalo:** %s (%d)
                    - **Setor Mais Impactado:** %s (%d)"""
                    .formatted(closedCount, slaCompliance, tagGargalo, tagGargaloQty, sectorMostImpacted, sectorMostImpactedQty);
            discordWebhookService.sendOperationalAlert("Relatório Semanal de TI", msgText);

        } catch (BeansException ex) {
            log.error("[DIGEST] Erro ao compilar e enviar o relatório semanal do Discord: {}", ex.getMessage(), ex);
        }
    }
}
