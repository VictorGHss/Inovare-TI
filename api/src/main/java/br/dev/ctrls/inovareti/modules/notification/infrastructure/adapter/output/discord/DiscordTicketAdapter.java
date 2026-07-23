package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord;

import java.util.List;
import java.util.ArrayList;
import java.util.EnumSet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.DiscordTicketPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Adapter that implements DiscordTicketPort using JDA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordTicketAdapter implements DiscordTicketPort {

    private final ObjectProvider<JDA> jdaProvider;
    private final TicketRepositoryPort ticketRepository;

    @Value("${discord.bot.guild-id:}")
    private String discordGuildId;

    private static final String ACTIVE_CATEGORY_ID = "1526959210863001751";
    private static final String ARCHIVED_CATEGORY_ID = "1526959741585063957";

    @Override
    @Transactional(readOnly = true)
    public void createTicketChannel(Ticket ticketParam, List<String> discordUserIds) {
        final Ticket ticket = ticketRepository.findById(ticketParam.getId()).orElse(ticketParam);
        log.info("[DISCORD-TICKET] Iniciando criação de canal para chamado #{} com {} usuários designados.",
                ticket.getNumber(), discordUserIds.size());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível. Criação do canal para chamado #{} ignorada.", ticket.getNumber());
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada. Criação do canal para chamado #{} abortada.", ticket.getNumber());
            return;
        }

        Category activeCategory = guild.getCategoryById(ACTIVE_CATEGORY_ID);
        if (activeCategory == null) {
            log.warn("[DISCORD-TICKET] Categoria de chamados ativos ({}) não encontrada.", ACTIVE_CATEGORY_ID);
            return;
        }
         // Resolve membros a serem permitidos no canal
        Member requesterMember = null;
        br.dev.ctrls.inovareti.modules.user.domain.model.User requester = ticket.getRequester();
        if (requester != null) {
            String requesterDiscordId = requester.getDiscordUserId();
            if (requesterDiscordId != null && !requesterDiscordId.isBlank()) {
                try {
                    requesterMember = guild.retrieveMemberById(java.util.Objects.requireNonNull(requesterDiscordId.trim())).complete();
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar criador do chamado no Discord: {}", requesterDiscordId, ex);
                }
            }
        }

        List<Member> allowedMembers = new ArrayList<>();
        for (String discordId : discordUserIds) {
            if (discordId != null && !discordId.isBlank()) {
                try {
                    Member m = guild.retrieveMemberById(java.util.Objects.requireNonNull(discordId.trim())).complete();
                    if (m != null) {
                        allowedMembers.add(m);
                    }
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar membro designado no Discord: {}", discordId, ex);
                }
            }
        }

        String suffix = "";
        if (ticket.getTitle() != null && !ticket.getTitle().isBlank()) {
            String normalized = java.text.Normalizer.normalize(ticket.getTitle(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-")
                    .replaceAll("-+", "-")
                    .trim();
            if (normalized.length() > 50) {
                normalized = normalized.substring(0, 50);
            }
            if (!normalized.isEmpty()) {
                suffix = "-" + normalized;
            }
        }

        String channelName = "ticket-" + ticket.getNumber().toLowerCase() + suffix;

        // Configura ações de override de permissão
        var channelAction = activeCategory.createTextChannel(channelName)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL));

        if (requesterMember != null) {
            channelAction = channelAction.addPermissionOverride(java.util.Objects.requireNonNull(requesterMember),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
        }

        for (Member member : allowedMembers) {
            channelAction = channelAction.addPermissionOverride(java.util.Objects.requireNonNull(member),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
        }

        channelAction.queue(
                channel -> {
                    log.info("[DISCORD-TICKET] Canal privado criado com sucesso: #{} (ID: {}) para chamado #{}",
                            channel.getName(), channel.getId(), ticket.getNumber());
                    sendAndPinInitialTicketMessage(channel, ticket);
                },
                error -> log.error("[DISCORD-TICKET] Falha ao criar canal privado para chamado #{}", ticket.getNumber(), error)
        );
    }

    @SuppressWarnings("null")
    private void sendAndPinInitialTicketMessage(TextChannel channel, Ticket ticket) {
        try {
            net.dv8tion.jda.api.EmbedBuilder eb = new net.dv8tion.jda.api.EmbedBuilder();
            String ticketNum = ticket.getNumber() != null ? ticket.getNumber() : "-";
            String ticketTitle = ticket.getTitle() != null ? ticket.getTitle() : "Sem título";
            eb.setTitle("🎫 Chamado #" + ticketNum + " - " + DiscordLgpdSanitizer.sanitize(ticketTitle));
            eb.setColor(0xFF9900); // Laranja operacoes

            String rawDescription = ticket.getDescription();
            if (rawDescription == null || rawDescription.isBlank()) {
                rawDescription = ticket.getTitle();
            }
            String sanitizedDescription = DiscordLgpdSanitizer.sanitize(rawDescription);
            eb.setDescription("**Descrição do Problema:**\n" + (sanitizedDescription != null ? sanitizedDescription : "-"));

            String requesterName = java.util.Objects.requireNonNullElse(
                    ticket.getRequester() != null ? DiscordLgpdSanitizer.sanitize(ticket.getRequester().getName()) : "-", "-");
            String requesterSector = java.util.Objects.requireNonNullElse(
                    ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "-", "-");
            String categoryName = java.util.Objects.requireNonNullElse(
                    ticket.getCategory() != null ? ticket.getCategory().getName() : "-", "-");
            String priority = java.util.Objects.requireNonNullElse(
                    ticket.getPriority() != null ? ticket.getPriority().toString() : "-", "-");
            String status = java.util.Objects.requireNonNullElse(
                    ticket.getStatus() != null ? ticket.getStatus().toString() : "OPEN", "OPEN");

            eb.addField("Solicitante", requesterName, true);
            eb.addField("Setor", requesterSector, true);
            eb.addField("Categoria", categoryName, true);
            eb.addField("Prioridade", priority, true);
            eb.addField("Status", status, true);

            if (ticket.getSlaDeadline() != null) {
                String formattedSla = java.util.Objects.requireNonNullElse(
                        ticket.getSlaDeadline().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), "-");
                eb.addField("Prazo SLA", formattedSla, true);
            }

            if (ticket.getRelatedTickets() != null && !ticket.getRelatedTickets().isEmpty()) {
                String linkedSummary = ticket.getRelatedTickets().stream()
                        .map(t -> "#" + (t.getNumber() != null ? t.getNumber() : "-") + " - " + DiscordLgpdSanitizer.sanitize(t.getTitle()))
                        .collect(java.util.stream.Collectors.joining("\n"));
                if (!linkedSummary.isBlank()) {
                    eb.addField("🔗 Chamados Vinculados", linkedSummary, false);
                }
            }

            String openedAt = ticket.getCreatedAt() != null
                    ? ticket.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "-";
            eb.setFooter("Inovare TI • Chamado aberto em: " + openedAt);
            eb.setTimestamp(java.time.Instant.now());

            channel.sendMessageEmbeds(eb.build()).queue(
                    message -> message.pin().queue(
                            v -> log.info("[DISCORD-TICKET] Mensagem inicial de detalhes fixada no canal #{}", channel.getName()),
                            pinErr -> log.warn("[DISCORD-TICKET] Falha ao fixar mensagem inicial no canal #{}: {}", channel.getName(), pinErr.getMessage())
                    ),
                    sendErr -> log.error("[DISCORD-TICKET] Falha ao enviar embed inicial para o canal #{}: {}", channel.getName(), sendErr.getMessage())
            );
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Erro ao montar ou enviar mensagem inicial fixada no canal #{}", channel.getName(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void archiveTicketChannel(Ticket ticket) {
        ticket = ticketRepository.findById(ticket.getId()).orElse(ticket);
        log.info("[DISCORD-TICKET] Iniciando arquivamento de canal para chamado #{}.", ticket.getNumber());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível. Arquivamento do canal para chamado #{} ignorado.", ticket.getNumber());
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada. Arquivamento do canal para chamado #{} abortada.", ticket.getNumber());
            return;
        }

        Category archivedCategory = guild.getCategoryById(ARCHIVED_CATEGORY_ID);
        if (archivedCategory == null) {
            log.warn("[DISCORD-TICKET] Categoria de chamados arquivados ({}) não encontrada.", ARCHIVED_CATEGORY_ID);
            return;
        }

        String prefix = "ticket-" + ticket.getNumber().toLowerCase();
        List<TextChannel> channels = new java.util.ArrayList<>();
        for (TextChannel tc : guild.getTextChannels()) {
            if (tc.getName().startsWith(prefix)) {
                channels.add(tc);
            }
        }

        if (channels.isEmpty()) {
            log.warn("[DISCORD-TICKET] Nenhum canal encontrado com o prefixo '{}' para o chamado #{}.",
                    prefix, ticket.getNumber());
            return;
        }

        for (TextChannel channel : channels) {
            // Envia embed de ticket resolvido
            net.dv8tion.jda.api.EmbedBuilder eb = new net.dv8tion.jda.api.EmbedBuilder();
            eb.setTitle("Ticket Resolvido");
            eb.setColor(0x00FF00); // Verde
            if (ticket.getAsset() != null) {
                eb.setDescription("Ativo baixado: Patrimônio " + ticket.getAsset().getPatrimonyCode());
            } else {
                eb.setDescription("Chamado resolvido com sucesso.");
            }
            channel.sendMessageEmbeds(eb.build()).queue(
                    v -> log.info("[DISCORD-TICKET] Embed de resolução enviado para canal #{}", channel.getName()),
                    err -> log.error("[DISCORD-TICKET] Falha ao enviar embed de resolução para canal #{}", channel.getName(), err)
            );

            // Remove permissão de escrita de todos os membros humanos vinculados
            for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                long targetId = override.getIdLong();
                if (targetId != jda.getSelfUser().getIdLong()) {
                    channel.getManager().putMemberPermissionOverride(
                            targetId,
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                            EnumSet.of(Permission.MESSAGE_SEND)
                    ).queue();
                }
            }

            // Move para a categoria de arquivados
            channel.getManager().setParent(archivedCategory).queue(
                    v -> log.info("[DISCORD-TICKET] Canal #{} movido com sucesso para a categoria de arquivados ({}).",
                            channel.getName(), archivedCategory.getName()),
                    error -> log.error("[DISCORD-TICKET] Falha ao mover canal #{} para arquivados.", channel.getName(), error)
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void notifyMerged(Ticket childTicket, Ticket parentTicket) {
        childTicket = ticketRepository.findById(childTicket.getId()).orElse(childTicket);
        parentTicket = ticketRepository.findById(parentTicket.getId()).orElse(parentTicket);
        log.info("[DISCORD-TICKET] Enviando notificação de unificação para canal do chamado filho #{}.", childTicket.getNumber());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível para notificar unificação.");
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada para notificar unificação.");
            return;
        }

        String prefix = "ticket-" + childTicket.getNumber().toLowerCase();
        List<TextChannel> channels = new java.util.ArrayList<>();
        for (TextChannel tc : guild.getTextChannels()) {
            if (tc.getName().startsWith(prefix)) {
                channels.add(tc);
            }
        }

        for (TextChannel channel : channels) {
            channel.sendMessage("🚨 Este chamado foi unificado ao Chamado Mestre #" + parentTicket.getNumber()).queue(
                    v -> log.info("[DISCORD-TICKET] Notificação de unificação enviada no canal #{}", channel.getName()),
                    err -> log.error("[DISCORD-TICKET] Erro ao enviar notificação de unificação no canal #{}", channel.getName(), err)
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void syncTicketChannelPermissions(Ticket ticket) {
        ticket = ticketRepository.findById(ticket.getId()).orElse(ticket);
        log.info("[DISCORD-TICKET] Sincronizando permissões do canal para chamado #{}.", ticket.getNumber());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível. Sincronização de permissões do canal para chamado #{} ignorada.", ticket.getNumber());
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada. Sincronização de permissões do canal para chamado #{} abortada.", ticket.getNumber());
            return;
        }

        String prefix = "ticket-" + ticket.getNumber().toLowerCase();
        List<TextChannel> channels = new java.util.ArrayList<>();
        for (TextChannel tc : guild.getTextChannels()) {
            if (tc.getName().startsWith(prefix)) {
                channels.add(tc);
            }
        }

        if (channels.isEmpty()) {
            log.warn("[DISCORD-TICKET] Nenhum canal encontrado com o prefixo '{}' para o chamado #{}.",
                    prefix, ticket.getNumber());
            return;
        }

        List<br.dev.ctrls.inovareti.modules.user.domain.model.User> candidates = new java.util.ArrayList<>();
        if (ticket.getRequester() != null) {
            candidates.add(ticket.getRequester());
        }
        if (ticket.getAssignedTo() != null) {
            candidates.add(ticket.getAssignedTo());
        }
        if (ticket.getAdditionalUsers() != null) {
            candidates.addAll(ticket.getAdditionalUsers());
        }

        List<Member> membersToPermit = new java.util.ArrayList<>();
        for (br.dev.ctrls.inovareti.modules.user.domain.model.User u : candidates) {
            if (u != null && u.getDiscordUserId() != null && !u.getDiscordUserId().isBlank()) {
                try {
                    Member m = guild.retrieveMemberById(java.util.Objects.requireNonNull(u.getDiscordUserId().trim())).complete();
                    if (m != null) {
                        membersToPermit.add(m);
                    }
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar membro {} ({}) no Discord para sincronização de permissões.",
                            u.getName(), u.getDiscordUserId(), ex);
                }
            }
        }

        for (TextChannel channel : channels) {
            for (Member m : membersToPermit) {
                channel.upsertPermissionOverride(java.util.Objects.requireNonNull(m))
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                        .queue(
                                v -> log.info("[DISCORD-TICKET] Permissões concedidas para {} no canal #{}", m.getUser().getAsTag(), channel.getName()),
                                err -> log.error("[DISCORD-TICKET] Falha ao upsert permissões para {} no canal #{}", m.getUser().getAsTag(), channel.getName(), err)
                        );
            }
        }
    }

    private Guild resolveGuild(JDA jda) {
        Guild guild = null;
        if (discordGuildId != null && !discordGuildId.isBlank()) {
            try {
                guild = jda.getGuildById(java.util.Objects.requireNonNull(discordGuildId.trim()));
            } catch (Exception ignored) {}
        }
        if (guild == null) {
            guild = jda.getGuilds().stream().findFirst().orElse(null);
        }
        return guild;
    }
}
