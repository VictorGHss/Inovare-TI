package br.dev.ctrls.inovareti.domain.notification.discord;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.transaction.annotation.Transactional;
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

    @Value("${discord.operational.webhook.url:}")
    private String operationalWebhookUrl;

    @Value("${discord.webhook.url:}")
    private String defaultWebhookUrl;

    @Value("${discord.thumbnail.url:}")
    private String discordThumbnailUrl;

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
        String webhook = StringUtils.hasText(operationalWebhookUrl) ? operationalWebhookUrl : defaultWebhookUrl;
        if (!StringUtils.hasText(webhook)) {
            log.warn("Operational Discord webhook not configured. Skipping operational alert: {}", title);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> embeds = new ArrayList<>();
        Map<String, Object> embed = new HashMap<>();

        embed.put("title", title != null ? title : "Alerta Operacional");
        embed.put("description", message != null ? message : "");
        embed.put("color", 16692588);

        String thumbnail = resolveThumbnailUrl();
        if (StringUtils.hasText(thumbnail)) {
            embed.put("thumbnail", Map.of("url", thumbnail));
        }

        String generatedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        embed.put("footer", Map.of("text", "Gerado em: " + generatedAt));

        embeds.add(embed);
        payload.put("embeds", embeds);

        try {
            restTemplate.postForEntity(webhook, new HttpEntity<>(payload, headers), Void.class);
            log.info("Operational alert enfileirada no Discord: {}", title);
        } catch (HttpClientErrorException.NotFound nf) {
            log.error("Webhook Discord inválido (404) para {}: {}", title, nf.getMessage());
            try {
                systemAlertRepository.save(SystemAlert.builder()
                        .alertType("DISCORD_OPERATIONAL_ALERT")
                        .severity("ERROR")
                        .source("DiscordWebhookService")
                        .title("Webhook Discord inválido (404) para: " + title)
                        .details(nf.getMessage())
                        .context(Map.of("webhook", webhook, "title", title))
                        .build());
            } catch (Exception e) {
                log.warn("Falha ao registrar SystemAlert após webhook inválido: {}", e.getMessage(), e);
            }
        } catch (RestClientException ex) {
            log.error("Falha ao enviar alerta operacional no Discord: {}", title, ex);
            try {
                SystemAlert alert = SystemAlert.builder()
                        .alertType("DISCORD_OPERATIONAL_ALERT")
                        .severity("ERROR")
                        .source("DiscordWebhookService")
                        .title("Falha ao enviar alerta operacional no Discord: " + title)
                        .details(ex.getMessage())
                        .context(Map.of("webhook", webhook, "title", title))
                        .build();

                systemAlertRepository.save(alert);
            } catch (Exception e) {
                log.warn("Falha ao registrar SystemAlert após falha no webhook do Discord: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar alerta operacional no Discord: {}", title, e);
            try {
                systemAlertRepository.save(SystemAlert.builder()
                        .alertType("DISCORD_OPERATIONAL_ALERT")
                        .severity("ERROR")
                        .source("DiscordWebhookService")
                        .title("Erro inesperado ao enviar alerta operacional: " + title)
                        .details(e.getMessage())
                        .context(Map.of("webhook", webhook, "title", title))
                        .build());
            } catch (Exception ex) {
                log.warn("Falha ao registrar SystemAlert após erro inesperado no webhook: {}", ex.getMessage(), ex);
            }
        }
    }

        private String resolveWebhookUrl(String configured, String settingKey) {
            // 1) environment variable (preferred)
            String env = System.getenv("DISCORD_WEBHOOK_URL");
            if (StringUtils.hasText(env)) return env;

            // 2) system_settings table
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

            // 3) configured property fallback
            if (StringUtils.hasText(configured)) return configured;
            return null;
        }

        // Removed unused synchronous overload doSendOperationalAlert(String, String)
        // to avoid unused-method warnings and keep only the ticket-specific sender.

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
            String webhook = resolveWebhookUrl(operationalWebhookUrl, "discord.operational.webhook.url");
            if (!StringUtils.hasText(webhook)) {
                webhook = resolveWebhookUrl(defaultWebhookUrl, "discord.webhook.url");
            }

            if (!StringUtils.hasText(webhook)) {
                log.warn("Operational Discord webhook not configured. Skipping operational alert: {}", ticket != null ? ticket.getId() : null);
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            List<Map<String, Object>> embeds = new ArrayList<>();
            Map<String, Object> embed = new HashMap<>();

            embed.put("title", "🚨 Novo Chamado Aberto");
            embed.put("color", 16692588);

            String url = ticket != null && ticket.getId() != null ? "https://itsm-inovare.ctrls.dev.br/tickets/" + ticket.getId().toString() : null;
            if (url != null) embed.put("url", url);

            String requester = ticket != null && ticket.getRequester() != null ? ticket.getRequester().getName() : "-";
            String sector = ticket != null && ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "-";
            String priority = ticket != null && ticket.getPriority() != null ? ticket.getPriority().toString() : "-";

            // Monta campos inline do embed com as informações chave do chamado
            // Campos solicitados: ID, Solicitante, Setor, Prioridade — todos em modo inline
            List<Map<String, Object>> fields = new ArrayList<>();
            String shortId = ticket != null && ticket.getId() != null ? ticket.getId().toString().substring(0, 8).toUpperCase() : "-";
            fields.add(Map.of("name", "ID", "value", shortId, "inline", true));
            fields.add(Map.of("name", "Solicitante", "value", requester, "inline", true));
            fields.add(Map.of("name", "Setor", "value", sector, "inline", true));
            fields.add(Map.of("name", "Prioridade", "value", priority, "inline", true));
            embed.put("fields", fields);

            String thumbnail = resolveThumbnailUrl();
            if (StringUtils.hasText(thumbnail)) {
                embed.put("thumbnail", Map.of("url", thumbnail));
            }

            String openedAt = ticket != null && ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "";
            embed.put("footer", Map.of("text", "Aberto em: " + openedAt));

            // short description
            embed.put("description", ticket != null && ticket.getTitle() != null ? ticket.getTitle() : "-");

            embeds.add(embed);
            payload.put("embeds", embeds);

            try {
                restTemplate.postForEntity(webhook, new HttpEntity<>(payload, headers), Void.class);
                log.info("Operational embed enfileirado no Discord para chamado {}", ticket != null ? ticket.getId() : null);
                return true;
            } catch (HttpClientErrorException.NotFound nf) {
                log.error("Webhook Discord inválido (404) para chamado {}: {}", ticket != null ? ticket.getId() : null, nf.getMessage());
                try {
                    systemAlertRepository.save(SystemAlert.builder()
                            .alertType("DISCORD_OPERATIONAL_ALERT")
                            .severity("ERROR")
                            .source("DiscordWebhookService")
                            .title("Webhook Discord inválido (404) para chamado: " + (ticket != null ? ticket.getId() : "?"))
                            .details(nf.getMessage())
                            .context(Map.of("webhook", webhook, "ticketId", ticket != null && ticket.getId() != null ? ticket.getId().toString() : ""))
                            .build());
                } catch (Exception e) {
                    log.warn("Falha ao registrar SystemAlert após webhook inválido: {}", e.getMessage(), e);
                }
                return false;
            } catch (RestClientException ex) {
                log.error("Falha ao enviar alerta operacional embed no Discord para chamado {}: {}", ticket != null ? ticket.getId() : null, ex.getMessage(), ex);
                try {
                    systemAlertRepository.save(SystemAlert.builder()
                            .alertType("DISCORD_OPERATIONAL_ALERT")
                            .severity("ERROR")
                            .source("DiscordWebhookService")
                            .title("Falha ao enviar alerta operacional embed no Discord para chamado: " + (ticket != null ? ticket.getId() : "?"))
                            .details(ex.getMessage())
                            .context(Map.of("webhook", webhook, "ticketId", ticket != null && ticket.getId() != null ? ticket.getId().toString() : ""))
                            .build());
                } catch (Exception e) {
                    log.warn("Falha ao registrar SystemAlert após falha no webhook do Discord: {}", e.getMessage(), e);
                }
                return false;
            } catch (Exception ex) {
                log.error("Erro inesperado ao enviar alerta operacional embed no Discord para chamado {}: {}", ticket != null ? ticket.getId() : null, ex.getMessage(), ex);
                try {
                    systemAlertRepository.save(SystemAlert.builder()
                            .alertType("DISCORD_OPERATIONAL_ALERT")
                            .severity("ERROR")
                            .source("DiscordWebhookService")
                            .title("Erro inesperado ao enviar alerta operacional embed no Discord para chamado: " + (ticket != null ? ticket.getId() : "?"))
                            .details(ex.getMessage())
                            .context(Map.of("webhook", webhook, "ticketId", ticket != null && ticket.getId() != null ? ticket.getId().toString() : ""))
                            .build());
                } catch (Exception e) {
                    log.warn("Falha ao registrar SystemAlert após erro inesperado no webhook do Discord: {}", e.getMessage(), e);
                }
                return false;
            }
        }

        public String getDefaultWebhookStatus() {
            String webhook = resolveWebhookUrl(defaultWebhookUrl, "discord.webhook.url");
            if (!StringUtils.hasText(webhook)) return "MISSING";
            try {
                restTemplate.headForHeaders(webhook);
                return "PRESENT";
            } catch (HttpClientErrorException.NotFound nf) {
                return "INVALID";
            } catch (RestClientException ex) {
                log.warn("Não foi possível verificar status do webhook: {}", ex.getMessage());
                return "UNKNOWN";
            }
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
