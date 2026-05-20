package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.domain.financeiro.SystemAlert;
import br.dev.ctrls.inovareti.domain.financeiro.SystemAlertRepository;
import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordInteractionListener;
import br.dev.ctrls.inovareti.domain.settings.SystemSetting;
import br.dev.ctrls.inovareti.domain.settings.SystemSettingRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Serviço de roteamento de notificações de chamados no Discord.
 *
 * Atua puramente como adaptador de despacho de rede (Outbound Adapter), delegando a montagem
 * de layouts para o DiscordEmbedBuilder e a resiliência/tentativas para o DiscordWebhookRetryUtility.
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
    private final SystemSettingRepository systemSettingRepository;
    private final TicketRepository ticketRepository;
    private final DiscordEmbedBuilder discordEmbedBuilder;
    private final DiscordWebhookRetryUtility discordWebhookRetryUtility;
    /** Provider lazy do JDA para envio de mensagens com botões interativos. */
    private final ObjectProvider<JDA> jdaProvider;

    @Value("${discord.operational.webhook.url:}")
    private String operationalWebhookUrl;

    @Value("${discord.webhook.url:}")
    private String defaultWebhookUrl;

    @Value("${discord.thumbnail.url:}")
    private String discordThumbnailUrl;

    @Value("${discord.operational.ticket-url-base:https://itsm-inovare.ctrls.dev.br/tickets/}")
    private String operationalTicketUrlBase;

    /**
     * Compatibilidade: aceita a entidade Ticket e encaminha para a versão que
     * carrega o chamado dentro de uma transação antes de disparar o envio
     * assíncrono. Isso previne LazyInitializationException.
     */
    public void sendNewTicketAlert(Ticket ticket) {
        if (ticket == null || ticket.getId() == null) {
            log.warn("sendNewTicketAlert chamado com ticket nulo ou sem id");
            return;
        }
        sendNewTicketAlert(ticket.getId());
    }

    /**
     * Carrega o Ticket com as relações necessárias dentro de uma transação
     * e delega para o envio assíncrono.
     */
    @Transactional(readOnly = true)
    public void sendNewTicketAlert(UUID ticketId) {
        if (ticketId == null) return;
        try {
            Optional<Ticket> maybe = ticketRepository.findByIdWithRelations(ticketId);
            if (maybe.isEmpty()) {
                log.warn("Chamado {} não encontrado para notificação Discord", ticketId);
                return;
            }
            Ticket fullTicket = maybe.get();
            sendNewTicketAlertAsync(fullTicket);
        } catch (Exception e) {
            log.error("Erro ao carregar chamado {} para notificação Discord: {}", ticketId, e.getMessage(), e);
        }
    }

    @Async
    private void sendNewTicketAlertAsync(Ticket ticket) {
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
                // Ainda assim, tenta notificar o canal operacional para garantir visibilidade.
                boolean sent = doSendOperationalAlert(ticket);
                if (!sent) {
                    // fallback: tentar notificar técnicos via DM
                    List<User> techs = userRepository.findAllByRoleInAndReceivesItNotificationsTrue(
                        List.of(UserRole.ADMIN, UserRole.TECHNICIAN));
                    log.warn("Operational webhook falhou — tentando fallback via DM para {} técnico(s)", techs.size());
                    for (User tech : techs) {
                        try {
                            if (tech.getDiscordUserId() == null || tech.getDiscordUserId().isBlank()) continue;
                            discordDirectMessageService.sendTicketUpdateDMToUser(
                                    tech.getDiscordUserId(), ticket.getId(), "ALERTA OPERACIONAL: " + title, description);
                        } catch (Exception e) {
                            log.warn("Falha ao enviar DM fallback para {}: {}", tech.getId(), e.getMessage());
                            try {
                                systemAlertRepository.save(SystemAlert.builder()
                                        .alertType("DISCORD_DM_FALLBACK_FAILURE")
                                        .severity("ERROR")
                                        .source("DiscordWebhookService")
                                        .title("Falha ao enviar fallback DM para técnico")
                                        .details(e.getMessage())
                                        .context(Map.of("ticketId", ticket.getId().toString(), "techId", tech.getId().toString()))
                                        .build());
                            } catch (Exception ex) {
                                log.warn("Falha ao registrar SystemAlert após falha DM fallback: {}", ex.getMessage(), ex);
                            }
                        }
                    }
                }
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
            boolean sent = doSendOperationalAlert(ticket);
            if (!sent) {
                // fallback: tentar notificar técnicos via DM
                List<User> techs = userRepository.findAllByRoleInAndReceivesItNotificationsTrue(
                    List.of(UserRole.ADMIN, UserRole.TECHNICIAN));
                log.warn("Operational webhook falhou — tentando fallback via DM para {} técnico(s)", techs.size());
                for (User tech : techs) {
                    try {
                        if (tech.getDiscordUserId() == null || tech.getDiscordUserId().isBlank()) continue;
                        discordDirectMessageService.sendTicketUpdateDMToUser(
                                tech.getDiscordUserId(), ticket.getId(), "ALERTA OPERACIONAL: " + title, description);
                    } catch (Exception e) {
                        log.warn("Falha ao enviar DM fallback para {}: {}", tech.getId(), e.getMessage());
                        try {
                            systemAlertRepository.save(SystemAlert.builder()
                                    .alertType("DISCORD_DM_FALLBACK_FAILURE")
                                    .severity("ERROR")
                                    .source("DiscordWebhookService")
                                    .title("Falha ao enviar fallback DM para técnico")
                                    .details(e.getMessage())
                                    .context(Map.of("ticketId", ticket.getId().toString(), "techId", tech.getId().toString()))
                                    .build());
                        } catch (Exception ex) {
                            log.warn("Falha ao registrar SystemAlert após falha DM fallback: {}", ex.getMessage(), ex);
                        }
                    }
                }
            }
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
        String webhook = resolveOperationalWebhook();
        if (!StringUtils.hasText(webhook)) {
            log.warn("Operational Discord webhook not configured. Skipping operational alert: {}", title);
            return;
        }

        Map<String, Object> embed = discordEmbedBuilder.buildOperationalAlertEmbed(
                title,
                message,
                resolveThumbnailUrl());

        discordWebhookRetryUtility.sendEmbedWithRetry(webhook, embed, "alerta operacional", title != null ? title : "sem título");
    }

    private String resolveWebhookUrl(String configured, String settingKey) {
        // 1) variável de ambiente (preferencial)
        String env = System.getenv("DISCORD_WEBHOOK_URL");
        if (StringUtils.hasText(env)) return env;

        // 2) tabela system_settings
        try {
            if (StringUtils.hasText(settingKey)) {
                Optional<SystemSetting> maybe = systemSettingRepository.findById(settingKey);
                if (maybe.isPresent() && StringUtils.hasText(maybe.get().getValue())) return maybe.get().getValue();
            }
            Optional<SystemSetting> maybeEnv = systemSettingRepository.findById("DISCORD_WEBHOOK_URL");
            if (maybeEnv.isPresent() && StringUtils.hasText(maybeEnv.get().getValue())) return maybeEnv.get().getValue();
        } catch (Exception e) {
            log.warn("Erro ao ler system_settings para chave {}: {}", settingKey, e.getMessage());
        }

        // 3) fallback para propriedade configurada
        if (StringUtils.hasText(configured)) return configured;
        return null;
    }

    private String resolveOperationalWebhook() {
        String webhook = resolveWebhookUrl(operationalWebhookUrl, "discord.operational.webhook.url");
        if (!StringUtils.hasText(webhook)) {
            webhook = resolveWebhookUrl(defaultWebhookUrl, "discord.webhook.url");
        }
        return webhook;
    }

    private String resolveThumbnailUrl() {
        String env = System.getenv("DISCORD_THUMBNAIL_URL");
        if (StringUtils.hasText(env)) return env;
        try {
            Optional<SystemSetting> maybe = systemSettingRepository.findById("discord.thumbnail.url");
            if (maybe.isPresent() && StringUtils.hasText(maybe.get().getValue())) return maybe.get().getValue();
        } catch (Exception e) {
            log.warn("Erro ao ler system_settings para discord.thumbnail.url: {}", e.getMessage());
        }
        if (StringUtils.hasText(discordThumbnailUrl)) return discordThumbnailUrl;
        return null;
    }

    /**
     * Envia um embed rico específico para notificações de chamados.
     * Retorna true em caso de sucesso.
     */
    private boolean doSendOperationalAlert(Ticket ticket) {
        String webhook = resolveOperationalWebhook();
        if (!StringUtils.hasText(webhook)) {
            log.warn("Operational Discord webhook not configured. Skipping operational alert: {}",
                    ticket != null ? ticket.getId() : null);
            return false;
        }

        Map<String, Object> embed = discordEmbedBuilder.buildNewTicketEmbed(
                ticket,
                resolveThumbnailUrl(),
                operationalTicketUrlBase);

        String ticketContext = ticket != null && ticket.getId() != null ? ticket.getId().toString() : "desconhecido";
        boolean enviado = discordWebhookRetryUtility.sendEmbedWithRetry(webhook, embed, "chamado", ticketContext);

        // Complemento via JDA: envia ao canal configurado com botões interativos de ação
        if (ticket != null && ticket.getId() != null) {
            enviarNotificacaoJdaComBotoes(ticket);
        }

        return enviado;
    }

    private String lastWebhookStatus = "UNKNOWN";
    private long lastWebhookStatusCheck = 0L;

    public String getDefaultWebhookStatus() {
        String webhook = resolveWebhookUrl(defaultWebhookUrl, "discord.webhook.url");
        if (!StringUtils.hasText(webhook)) return "MISSING";

        long now = System.currentTimeMillis();
        if (now - lastWebhookStatusCheck < 15 * 60 * 1000) {
            return lastWebhookStatus;
        }

        try {
            restTemplate.headForHeaders(webhook);
            lastWebhookStatus = "PRESENT";
        } catch (HttpClientErrorException.NotFound nf) {
            lastWebhookStatus = "INVALID";
        } catch (RestClientException ex) {
            log.warn("Não foi possível verificar status do webhook: {}", ex.getMessage());
            lastWebhookStatus = "UNKNOWN";
        }

        lastWebhookStatusCheck = now;
        return lastWebhookStatus;
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

    /**
     * Envia notificação de novo chamado via JDA (bot) para o canal operacional,
     * incluindo um {@link net.dv8tion.jda.api.interactions.components.ActionRow}
     * com botões nativos de 'Assumir' e 'Recusar'.
     *
     * <p>O canal é resolvido pela propriedade {@code discord.bot.operational-channel-id}.
     * Se o JDA não estiver disponível ou o canal não estiver configurado, o método
     * é ignorado silenciosamente (graceful degradation).
     */
    @org.springframework.beans.factory.annotation.Value("${discord.bot.operational-channel-id:}")
    private String operationalChannelId;

    private void enviarNotificacaoJdaComBotoes(Ticket ticket) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.debug("[JDA] Bot Discord indisponível — notificação com botões ignorada para chamado {}",
                    ticket.getId());
            return;
        }

        if (operationalChannelId == null || operationalChannelId.isBlank()) {
            log.debug("[JDA] 'discord.bot.operational-channel-id' não configurado — pulando notificação JDA para chamado {}",
                    ticket.getId());
            return;
        }

        try {
            TextChannel canal = jda.getTextChannelById(operationalChannelId);
            if (canal == null) {
                log.warn("[JDA] Canal operacional '{}' não encontrado. Verifique 'discord.bot.operational-channel-id'.",
                        operationalChannelId);
                return;
            }

            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            String solicitante = ticket.getRequester() != null ? ticket.getRequester().getName() : "-";
            String setor = ticket.getRequester() != null && ticket.getRequester().getSector() != null
                    ? ticket.getRequester().getSector().getName() : "-";
            String prioridade = ticket.getPriority() != null ? ticket.getPriority().name() : "-";

            var embed = new EmbedBuilder()
                    .setColor(DiscordEmbedBuilder.OPERATIONS_COLOR)
                    .setTitle("🚨 Novo Chamado Aberto — #" + shortId)
                    .setDescription(ticket.getTitle())
                    .addField("Solicitante", solicitante, true)
                    .addField("Setor", setor, true)
                    .addField("Prioridade", prioridade, true)
                    .setFooter("Inovare TI • Clique em Assumir para atribuir o chamado a você")
                    .build();

            canal.sendMessageEmbeds(embed)
                    .setComponents(DiscordInteractionListener.criarBotoesDeAcao(ticket.getId()))
                    .queue(
                            ok  -> log.info("[JDA] Notificação com botões enviada ao canal '{}' para chamado {}",
                                    operationalChannelId, ticket.getId()),
                            err -> log.warn("[JDA] Falha ao enviar notificação com botões para chamado {}: {}",
                                    ticket.getId(), err.getMessage())
                    );
        } catch (Exception ex) {
            log.warn("[JDA] Erro inesperado ao enviar notificação JDA com botões para chamado {}: {}",
                    ticket.getId(), ex.getMessage());
        }
    }
}
