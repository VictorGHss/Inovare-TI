package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord;

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

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.port.SystemAlertRepository;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordInteractionListener;
import br.dev.ctrls.inovareti.modules.settings.domain.model.SystemSetting;
import br.dev.ctrls.inovareti.modules.settings.domain.port.output.SystemSettingRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * ServiíÂ§o de roteamento de notificaíÂ§íÂµes de chamados no Discord.
 *
 * Atua puramente como adaptador de despacho de rede (Outbound Adapter), delegando a montagem
 * de layouts para o DiscordEmbedBuilder e a resiliíÂªncia/tentativas para o DiscordWebhookRetryUtility.
 *
 * Regras de distribuiíÂ§íÂ£o:
 * 1) Chamado sem tíÂ©cnico responsíÂ¡vel: notifica apenas ADMIN/TECHNICIAN com
 *    receives_it_notifications = true.
 * 2) Chamado assumido: notifica apenas o tíÂ©cnico responsíÂ¡vel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

    private final UserRepositoryPort userRepository;
    private final DiscordDirectMessageService discordDirectMessageService;
    private final RestTemplate restTemplate;
    private final SystemAlertRepository systemAlertRepository;
    private final SystemSettingRepositoryPort systemSettingRepository;
    private final TicketRepositoryPort ticketRepository;
    private final DiscordEmbedBuilder discordEmbedBuilder;
    private final DiscordWebhookRetryUtility discordWebhookRetryUtility;
    /** Provider lazy do JDA para envio de mensagens com botíÂµes interativos. */
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
     * Compatibilidade: aceita a entidade Ticket e encaminha para a versíÂ£o que
     * carrega o chamado dentro de uma transaíÂ§íÂ£o antes de disparar o envio
     * assíÂ­ncrono. Isso previne LazyInitializationException.
     */
    public void sendNewTicketAlert(Ticket ticket) {
        if (ticket == null || ticket.getId() == null) {
            log.warn("sendNewTicketAlert chamado com ticket nulo ou sem id");
            return;
        }
        sendNewTicketAlert(ticket.getId());
    }

    /**
     * Carrega o Ticket com as relaíÂ§íÂµes necessíÂ¡rias dentro de uma transaíÂ§íÂ£o
     * e delega para o envio assíÂ­ncrono.
     */
    @Transactional(readOnly = true)
    public void sendNewTicketAlert(UUID ticketId) {
        if (ticketId == null) return;
        try {
            Optional<Ticket> maybe = ticketRepository.findByIdWithRelations(ticketId);
            if (maybe.isEmpty()) {
                log.warn("Chamado {} níÂ£o encontrado para notificaíÂ§íÂ£o Discord", ticketId);
                return;
            }
            Ticket fullTicket = maybe.get();
            sendNewTicketAlertAsync(fullTicket);
        } catch (Exception e) {
            log.error("Erro ao carregar chamado {} para notificaíÂ§íÂ£o Discord: {}", ticketId, e.getMessage(), e);
        }
    }

    @Async
    private void sendNewTicketAlertAsync(Ticket ticket) {
        try {
            validateTicket(ticket);
            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            String title = ticket.getAssignedTo() == null ? "Novo chamado aberto" : "Atualização de chamado";
            String rawDescription = ticket.getAssignedTo() == null
                    ? String.format("Chamado #%s aberto: %s", shortId, ticket.getTitle())
                    : String.format("Chamado #%s em acompanhamento: %s", shortId, ticket.getTitle());
            String description = DiscordLgpdSanitizer.sanitize(rawDescription);

            List<User> recipients = resolveRecipients(ticket);
            if (recipients.isEmpty()) {
                log.info("NotificaíÂ§íÂ£o Discord ignorada para o chamado {}: nenhum destinatíÂ¡rio elegíÂ­vel", ticket.getId());
                // Ainda assim, tenta notificar o canal operacional para garantir visibilidade.
                boolean sent = doSendOperationalAlert(ticket);
                if (!sent) {
                    // fallback: tentar notificar tíÂ©cnicos via DM
                    List<User> techs = userRepository.findAllByRoleInAndReceivesItNotificationsTrue(
                        List.of(UserRole.ADMIN, UserRole.TECHNICIAN));
                    log.warn("Operational webhook falhou ââ‚¬â€ tentando fallback via DM para {} tíÂ©cnico(s)", techs.size());
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
                                        .title("Falha ao enviar fallback DM para tíÂ©cnico")
                                        .details(e.getMessage())
                                        .context(Map.of("ticketId", ticket.getId().toString(), "techId", tech.getId().toString()))
                                        .build());
                            } catch (Exception ex) {
                                log.warn("Falha ao registrar SystemAlert apíÂ³s falha DM fallback: {}", ex.getMessage(), ex);
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

            log.info("NotificaíÂ§íÂµes Discord enfileiradas para o chamado {} para {} destinatíÂ¡rio(s)", ticket.getId(), recipients.size());

            // Sempre enviar uma notificaíÂ§íÂ£o ao canal operacional para que a
            // equipe de operaíÂ§íÂµes receba um resumo do novo chamado.
            boolean sent = doSendOperationalAlert(ticket);
            if (!sent) {
                // fallback: tentar notificar tíÂ©cnicos via DM
                List<User> techs = userRepository.findAllByRoleInAndReceivesItNotificationsTrue(
                    List.of(UserRole.ADMIN, UserRole.TECHNICIAN));
                log.warn("Operational webhook falhou ââ‚¬â€ tentando fallback via DM para {} tíÂ©cnico(s)", techs.size());
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
                                    .title("Falha ao enviar fallback DM para tíÂ©cnico")
                                    .details(e.getMessage())
                                    .context(Map.of("ticketId", ticket.getId().toString(), "techId", tech.getId().toString()))
                                    .build());
                        } catch (Exception ex) {
                            log.warn("Falha ao registrar SystemAlert apíÂ³s falha DM fallback: {}", ex.getMessage(), ex);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            UUID ticketId = ticket != null ? ticket.getId() : null;
            log.error("Erro de validaíÂ§íÂ£o no roteamento de notificaíÂ§íÂ£o Discord para o chamado {}", ticketId, e);
        }
    }

    /**
     * Envia uma mensagem ao canal operacional (webhook) do Discord.
     * MíÂ©todo assíÂ­ncrono e tolerante a falhas para níÂ£o impactar o fluxo principal.
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

        discordWebhookRetryUtility.sendEmbedWithRetry(webhook, embed, "alerta operacional", title != null ? title : "sem tíÂ­tulo");
    }

    private String resolveWebhookUrl(String configured, String settingKey) {
        // 1) variíÂ¡vel de ambiente (preferencial)
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
     * Envia um embed rico especíÂ­fico para notificaíÂ§íÂµes de chamados.
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

        // Complemento via JDA: envia ao canal configurado com botíÂµes interativos de aíÂ§íÂ£o
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
            log.warn("NíÂ£o foi possíÂ­vel verificar status do webhook: {}", ex.getMessage());
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
     * @throws IllegalArgumentException se a validaíÂ§íÂ£o falhar
     */
    private void validateTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Chamado níÂ£o pode ser nulo");
        }
        if (ticket.getRequester() == null) {
            throw new IllegalArgumentException("Solicitante do chamado níÂ£o pode ser nulo");
        }
        if (ticket.getPriority() == null) {
            throw new IllegalArgumentException("Prioridade do chamado níÂ£o pode ser nula");
        }
        if (ticket.getCategory() == null) {
            throw new IllegalArgumentException("Categoria do chamado níÂ£o pode ser nula");
        }
    }

    /**
     * Envia notificaíÂ§íÂ£o de novo chamado via JDA (bot) para o canal operacional,
     * incluindo um {@link net.dv8tion.jda.api.interactions.components.ActionRow}
     * com botíÂµes nativos de 'Assumir' e 'Recusar'.
     *
     * <p>O canal íÂ© resolvido pela propriedade {@code discord.bot.operational-channel-id}.
     * Se o JDA níÂ£o estiver disponíÂ­vel ou o canal níÂ£o estiver configurado, o míÂ©todo
     * íÂ© ignorado silenciosamente (graceful degradation).
     */
    @org.springframework.beans.factory.annotation.Value("${discord.bot.operational-channel-id:}")
    private String operationalChannelId;

    @SuppressWarnings("null")
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
                log.warn("[JDA] Canal operacional '{}' níÂ£o encontrado. Verifique 'discord.bot.operational-channel-id'.",
                        operationalChannelId);
                return;
            }

            String shortId     = ticket.getId().toString().substring(0, 8).toUpperCase();
            String rawSolicitante = ticket.getRequester() != null
                    ? Objects.requireNonNullElse(ticket.getRequester().getName(), "-") : "-";
            String solicitante = DiscordLgpdSanitizer.sanitize(rawSolicitante);
            String setor       = ticket.getRequester() != null && ticket.getRequester().getSector() != null
                    ? Objects.requireNonNullElse(ticket.getRequester().getSector().getName(), "-") : "-";
            String prioridade  = ticket.getPriority() != null
                    ? ticket.getPriority().name() : "-";

            String rawDescription = ticket.getTitle();
            String description = DiscordLgpdSanitizer.sanitize(rawDescription);

            var embed = new EmbedBuilder()
                    .setColor(DiscordEmbedBuilder.OPERATIONS_COLOR)
                    .setTitle("🚨 Novo Chamado Aberto — #" + shortId)
                    .setDescription(description)
                    .addField("Solicitante", solicitante, true)
                    .addField("Setor", setor, true)
                    .addField("Prioridade", prioridade, true)
                    .setFooter("Inovare TI • Clique em Assumir para atribuir o chamado a você")
                    .build();

            canal.sendMessageEmbeds(embed)
                    .setComponents(DiscordInteractionListener.criarBotoesDeAcao(ticket.getId()))
                    .queue(
                            ok  -> log.info("[JDA] NotificaíÂ§íÂ£o com botíÂµes enviada ao canal '{}' para chamado {}",
                                    operationalChannelId, ticket.getId()),
                            err -> log.warn("[JDA] Falha ao enviar notificaíÂ§íÂ£o com botíÂµes para chamado {}: {}",
                                    ticket.getId(), err.getMessage())
                    );
        } catch (Exception ex) {
            log.warn("[JDA] Erro inesperado ao enviar notificaíÂ§íÂ£o JDA com botíÂµes para chamado {}: {}",
                    ticket.getId(), ex.getMessage());
        }
    }
}

