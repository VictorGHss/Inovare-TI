package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import br.dev.ctrls.inovareti.domain.settings.SystemSetting;
import br.dev.ctrls.inovareti.domain.settings.SystemSettingRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de roteamento de notificações de chamados no Discord.
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

    @Value("${discord.operational.webhook.url:}")
    private String operationalWebhookUrl;

    @Value("${discord.webhook.url:}")
    private String defaultWebhookUrl;

    @Value("${discord.thumbnail.url:}")
    private String discordThumbnailUrl;

    @Value("${discord.operational.ticket-url-base:https://itsm-inovare.ctrls.dev.br/tickets/}")
    private String operationalTicketUrlBase;

    @Value("${discord.webhook.retry.max-attempts:3}")
    private int webhookMaxAttempts;

    @Value("${discord.webhook.retry.backoff-ms:500}")
    private long webhookRetryBackoffMs;

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

        sendEmbedWithRetry(webhook, embed, "alerta operacional", title != null ? title : "sem título");
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

        private boolean sendEmbedWithRetry(
                String webhook,
                Map<String, Object> embed,
                String contextType,
                String contextId) {
            if (!StringUtils.hasText(webhook)) {
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of("embeds", List.of(embed));
            int maxAttempts = Math.max(webhookMaxAttempts, 1);

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    restTemplate.postForEntity(webhook, new HttpEntity<>(payload, headers), Void.class);
                    log.info("Embed do Discord enviado com sucesso para {}={} na tentativa {}/{}.",
                            contextType,
                            contextId,
                            attempt,
                            maxAttempts);
                    return true;
                } catch (HttpClientErrorException.NotFound nf) {
                    log.error("Webhook Discord inválido (404) para {}={}: {}", contextType, contextId, nf.getMessage());
                    registerOperationalSendFailure(
                            "Webhook Discord inválido (404)",
                            nf.getMessage(),
                            webhook,
                            contextType,
                            contextId);
                    return false;
                } catch (RestClientException ex) {
                    if (attempt < maxAttempts) {
                        log.warn("Falha ao enviar embed no Discord para {}={} (tentativa {}/{}). Retentando...",
                                contextType,
                                contextId,
                                attempt,
                                maxAttempts);
                        waitBeforeRetry();
                        continue;
                    }

                    log.error("Falha ao enviar embed no Discord para {}={} após {} tentativa(s): {}",
                            contextType,
                            contextId,
                            maxAttempts,
                            ex.getMessage(),
                            ex);
                    registerOperationalSendFailure(
                            "Falha ao enviar embed no Discord",
                            ex.getMessage(),
                            webhook,
                            contextType,
                            contextId);
                    return false;
                } catch (Exception ex) {
                    if (attempt < maxAttempts) {
                        log.warn("Erro inesperado ao enviar embed no Discord para {}={} (tentativa {}/{}). Retentando...",
                                contextType,
                                contextId,
                                attempt,
                                maxAttempts,
                                ex);
                        waitBeforeRetry();
                        continue;
                    }

                    log.error("Erro inesperado ao enviar embed no Discord para {}={} após {} tentativa(s): {}",
                            contextType,
                            contextId,
                            maxAttempts,
                            ex.getMessage(),
                            ex);
                    registerOperationalSendFailure(
                            "Erro inesperado ao enviar embed no Discord",
                            ex.getMessage(),
                            webhook,
                            contextType,
                            contextId);
                    return false;
                }
            }

            return false;
        }

        private void waitBeforeRetry() {
            try {
                Thread.sleep(Math.max(webhookRetryBackoffMs, 0L));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrompida durante backoff de retry do webhook Discord.");
            }
        }

        private void registerOperationalSendFailure(
                String title,
                String details,
                String webhook,
                String contextType,
                String contextId) {
            String resolvedDetails = StringUtils.hasText(details) ? details : "Sem detalhes";
            try {
                systemAlertRepository.save(SystemAlert.builder()
                        .alertType("DISCORD_OPERATIONAL_ALERT")
                        .severity("ERROR")
                        .source("DiscordWebhookService")
                        .title(title)
                        .details(resolvedDetails)
                        .context(Map.of(
                                "webhook", webhook,
                                "contextType", contextType,
                                "contextId", contextId))
                        .build());
            } catch (Exception ex) {
                log.warn("Falha ao registrar SystemAlert após erro no webhook do Discord: {}", ex.getMessage(), ex);
            }
        }

        // Removido overload síncrono não utilizado doSendOperationalAlert(String, String)
        // para evitar warnings de método não utilizado e manter apenas o envio específico por chamado.

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
            return sendEmbedWithRetry(webhook, embed, "chamado", ticketContext);
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
}
