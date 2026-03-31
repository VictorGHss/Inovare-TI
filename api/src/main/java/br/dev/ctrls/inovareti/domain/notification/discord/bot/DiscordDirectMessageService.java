package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.SystemAlert;
import br.dev.ctrls.inovareti.domain.financeiro.SystemAlertRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.utils.FileUpload;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordDirectMessageService {

    /**
     * Serviço assíncrono para envio de mensagens diretas (DM) via Discord.
     *
     * Ele encapsula a lógica de construção de embeds, verificação de disponibilidade
     * do cliente JDA e trata casos comuns (usuário sem Discord vinculado,
     * JDA indisponível, etc.). As mensagens exibidas aos usuários são em
     * Português e voltadas para operações do domínio (chamados, 2FA, recibos).
     */

    private static final int CLINIC_BRAND_COLOR = 0xF97316;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final ObjectProvider<JDA> jdaProvider;
    private final SystemAlertRepository systemAlertRepository;

    @Async
    public void sendTicketUpdateDM(Ticket ticket, String title, String description) {
        /**
         * Envia uma DM para o solicitante do chamado com um resumo da atualização.
         * Método assíncrono que retorna imediatamente; falhas são registradas em
         * log, mas não propagadas.
         *
         * @param ticket entidade do chamado
         * @param title título do embed
         * @param description descrição/conteúdo da mensagem
         */
        if (ticket == null || ticket.getRequester() == null) {
            log.warn("Ignoring Discord DM: ticket or requester is null");
            return;
        }

        String discordUserId = ticket.getRequester().getDiscordUserId();
        sendTicketUpdateDMToUser(discordUserId, ticket.getId(), title, description);
    }

    @Async
    public void sendTicketUpdateDMToUser(String discordUserId, UUID ticketId, String title, String description) {
        if (ticketId == null) {
            log.warn("Ignoring Discord DM: ticket id is null");
            return;
        }

        if (discordUserId == null || discordUserId.isBlank()) {
            log.info("Ignoring Discord DM for ticket {}: target user has no Discord id", ticketId);
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("Ignoring Discord DM for ticket {}: JDA is not available", ticketId);
            try {
                SystemAlert alert = SystemAlert.builder()
                        .alertType("DISCORD_DM_FAILURE")
                        .severity("ERROR")
                        .source("DiscordDirectMessageService")
                        .title("JDA não disponível para envio de DM")
                        .details("JDA provider retornou null ao tentar enviar DM")
                        .context(Map.of("ticketId", ticketId.toString(), "discordUserId", discordUserId))
                        .build();
                systemAlertRepository.save(alert);
            } catch (Exception ex) {
                log.warn("Falha ao salvar SystemAlert após JDA indisponível: {}", ex.getMessage(), ex);
            }
            return;
        }

        String ticketUrl = buildTicketUrl(ticketId);
        String content = description + "\n\n[View Ticket](" + ticketUrl + ")";

        var embed = new EmbedBuilder()
                .setColor(CLINIC_BRAND_COLOR)
                .setTitle(title)
                .setDescription(content)
                .build();

        jda.retrieveUserById(discordUserId).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessageEmbeds(embed).queue(
                    success -> log.info("Discord DM sent for ticket {} to user {}", ticketId, discordUserId),
                    error -> {
                        log.warn("Failed to send Discord DM for ticket {} to user {}", ticketId, discordUserId, error);
                        try {
                            SystemAlert alert = SystemAlert.builder()
                                    .alertType("DISCORD_DM_FAILURE")
                                    .severity("ERROR")
                                    .source("DiscordDirectMessageService")
                                    .title("Falha ao enviar DM no Discord")
                                    .details(error != null ? error.getMessage() : "Unknown error")
                                    .context(Map.of("ticketId", ticketId.toString(), "discordUserId", discordUserId))
                                    .build();
                            systemAlertRepository.save(alert);
                            } catch (Exception ex) {
                            log.warn("Falha ao salvar SystemAlert para DM failure: {}", ex.getMessage(), ex);
                        }
                    }
                        ),
                error -> {
                    log.warn("Failed to open Discord DM channel for ticket {} to user {}", ticketId, discordUserId, error);
                    try {
                        SystemAlert alert = SystemAlert.builder()
                                .alertType("DISCORD_DM_FAILURE")
                                .severity("ERROR")
                                .source("DiscordDirectMessageService")
                                .title("Falha ao abrir canal DM no Discord")
                                .details(error != null ? error.getMessage() : "Unknown error")
                                .context(Map.of("ticketId", ticketId.toString(), "discordUserId", discordUserId))
                                .build();
                        systemAlertRepository.save(alert);
                        } catch (Exception ex) {
                        log.warn("Falha ao salvar SystemAlert para DM open failure: {}", ex.getMessage(), ex);
                    }
                }
                ),
            error -> {
                log.warn("Failed to retrieve Discord user {} for ticket {}", discordUserId, ticketId, error);
                try {
                    SystemAlert alert = SystemAlert.builder()
                            .alertType("DISCORD_DM_FAILURE")
                            .severity("ERROR")
                            .source("DiscordDirectMessageService")
                            .title("Falha ao recuperar usuário no Discord")
                            .details(error != null ? error.getMessage() : "Unknown error")
                            .context(Map.of("ticketId", ticketId.toString(), "discordUserId", discordUserId))
                            .build();
                    systemAlertRepository.save(alert);
                    } catch (Exception ex) {
                    log.warn("Falha ao salvar SystemAlert para user retrieve failure: {}", ex.getMessage(), ex);
                }
            }
        );
    }

    /**
     * Envia um arquivo PDF via DM para o usuário especificado no Discord.
     * Método assíncrono; falhas são registradas mas não propagadas.
     */
    @Async
    public void sendReportPdfDMToUser(String discordUserId, byte[] pdfBytes, String filename, String message) {
        if (discordUserId == null || discordUserId.isBlank()) {
            log.info("Ignoring report PDF DM: target user has no Discord id");
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("Ignoring report PDF DM: JDA is not available");
            return;
        }

        jda.retrieveUserById(discordUserId).queue(
            user -> user.openPrivateChannel().queue(
                channel -> {
                    try {
                        if (message != null && !message.isBlank()) {
                            channel.sendMessage(message).queue();
                        }
                        channel.sendFiles(FileUpload.fromData(pdfBytes, filename)).queue(
                            success -> log.info("Report PDF DM sent to user {}", discordUserId),
                            error -> log.warn("Failed to send Report PDF DM to user {}", discordUserId, error)
                        );
                    } catch (Exception ex) {
                        log.warn("Unexpected error sending PDF DM to {}", discordUserId, ex);
                    }
                },
                error -> log.warn("Failed to open Discord DM channel for user {}", discordUserId, error)
            ),
            error -> log.warn("Failed to retrieve Discord user {}", discordUserId, error)
        );
    }

        private String buildTicketUrl(UUID ticketId) {
        String normalizedBaseUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:5173";
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        return normalizedBaseUrl + "/tickets/" + ticketId;
    }

    /**
     * Envia o código de recuperação do 2FA ao usuário via DM no Discord.
     * Execução síncrona para garantir o retorno do código antes da resposta da API.
     *
     * @param discordUserId ID do usuário no Discord
     * @param code          Código de 8 caracteres gerado para a recuperação
     * @param userName      Nome exibido no embed para contextualizar ao usuário
     */
    public void sendTwoFactorResetCode(String discordUserId, String code, String userName) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("Ignoring 2FA reset DM for {}: JDA is not available", discordUserId);
            return;
        }

        if (discordUserId == null || discordUserId.isBlank()) {
            log.error("Failed to send 2FA reset DM: invalid Discord user id '{}'.", discordUserId);
            return;
        }

        var embed = new EmbedBuilder()
            .setColor(CLINIC_BRAND_COLOR)
            .setTitle("🔐 Recuperação de Autenticação 2FA — Inovare TI")
            .setDescription(
                "Olá, **" + userName + "**!\n\n"
                + "Seu código de recuperação para redefinir o 2FA é:\n\n"
                + "```\n" + code + "\n```\n"
                + "⚠️ O código expira em **15 minutos** e é de uso único.\n"
                + "Se você não solicitou esta recuperação, ignore esta mensagem.")
            .build();

        try {
            jda.retrieveUserById(discordUserId).complete()
                    .openPrivateChannel().complete()
                    .sendMessageEmbeds(embed).complete();
                log.info("2FA reset code sent via Discord DM to user {}", discordUserId);
        } catch (Exception ex) {
                log.error(
                    "Failed to send 2FA reset DM to Discord user {}. Possible cause: invalid ID, DM blocked or bot permission. Error: {}",
                    discordUserId,
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Notifica o usuário quando um administrador reseta o seu 2FA.
     */
    public void sendTwoFactorResetByAdminNotification(String discordUserId, String targetUserName, String adminName) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("Ignoring 2FA reset-by-admin notification for {}: JDA is not available", discordUserId);
            return;
        }

        if (discordUserId == null || discordUserId.isBlank()) {
            log.error("Failed to send 2FA reset-by-admin notification: invalid Discord user id '{}'.", discordUserId);
            return;
        }

        var embed = new EmbedBuilder()
            .setColor(CLINIC_BRAND_COLOR)
            .setTitle("🔐 Seu 2FA foi resetado")
            .setDescription(
                "Olá, **" + targetUserName + "**!\n\n"
                    + "Seu Segundo Fator de Autenticação (2FA) foi resetado por um administrador.\n"
                    + "Por favor, reconfigure-o no seu próximo acesso.\n\n"
                    + "Administrador responsável: **" + adminName + "**")
            .build();

        try {
            jda.retrieveUserById(discordUserId).complete()
                    .openPrivateChannel().complete()
                    .sendMessageEmbeds(embed).complete();
                log.info("2FA reset-by-admin notification sent via Discord DM to user {}", discordUserId);
        } catch (Exception ex) {
                log.error(
                    "Failed to send 2FA reset-by-admin DM to Discord user {}. Possible cause: invalid ID, DM blocked or bot permission. Error: {}",
                    discordUserId,
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Envia notificação de recibo financeiro via DM ao médico vinculado.
     */
    public void sendFinancialReceiptNotification(String discordUserId, String medicoNome, String parcelaId) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            throw new IllegalStateException("JDA unavailable for sending financial receipt notification via Discord.");
        }

        if (discordUserId == null || discordUserId.isBlank()) {
            throw new IllegalArgumentException("User has no Discord linked to receive financial receipt.");
        }

        var embed = new EmbedBuilder()
            .setColor(CLINIC_BRAND_COLOR)
            .setTitle("📄 Recibo Financeiro Disponível")
            .setDescription(
                "Olá, **" + medicoNome + "**!\n\n"
                    + "Seu recibo da parcela **" + parcelaId + "** foi processado com sucesso no módulo financeiro.")
            .build();

        try {
            jda.retrieveUserById(discordUserId).complete()
                    .openPrivateChannel().complete()
                    .sendMessageEmbeds(embed).complete();
            log.info("Financial receipt notification sent via Discord DM to user {} for parcel {}", discordUserId, parcelaId);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send receipt notification via Discord.", ex);
        }
    }
}
