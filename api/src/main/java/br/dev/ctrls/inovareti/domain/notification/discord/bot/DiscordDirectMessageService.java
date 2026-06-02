package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.port.SystemAlertRepository;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
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
     * ServiÃƒÂ§o assÃƒÂ­ncrono para envio de mensagens diretas (DM) via Discord.
     *
     * Ele encapsula a lÃƒÂ³gica de construÃƒÂ§ÃƒÂ£o de embeds, verificaÃƒÂ§ÃƒÂ£o de disponibilidade
     * do cliente JDA e trata casos comuns (usuÃƒÂ¡rio sem Discord vinculado,
     * JDA indisponÃƒÂ­vel, etc.). As mensagens exibidas aos usuÃƒÂ¡rios sÃƒÂ£o em
     * PortuguÃƒÂªs e voltadas para operaÃƒÂ§ÃƒÂµes do domÃƒÂ­nio (chamados, 2FA, recibos).
     */

    private static final int CLINIC_BRAND_COLOR = 0xF97316;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final ObjectProvider<JDA> jdaProvider;
    private final SystemAlertRepository systemAlertRepository;

    @Async
    public void sendTicketUpdateDM(Ticket ticket, String title, String description) {
        /**
         * Envia uma DM para o solicitante do chamado com um resumo da atualizaÃƒÂ§ÃƒÂ£o.
         * MÃƒÂ©todo assÃƒÂ­ncrono que retorna imediatamente; falhas sÃƒÂ£o registradas em
         * log, mas nÃƒÂ£o propagadas.
         *
         * @param ticket entidade do chamado
         * @param title tÃƒÂ­tulo do embed
         * @param description descriÃƒÂ§ÃƒÂ£o/conteÃƒÂºdo da mensagem
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
                        .title("JDA nÃƒÂ£o disponÃƒÂ­vel para envio de DM")
                        .details("JDA provider retornou null ao tentar enviar DM")
                        .context(Map.of("ticketId", ticketId.toString(), "discordUserId", discordUserId))
                        .build();
                systemAlertRepository.save(alert);
            } catch (Exception ex) {
                log.warn("Falha ao salvar SystemAlert apÃƒÂ³s JDA indisponÃƒÂ­vel: {}", ex.getMessage(), ex);
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
                            .title("Falha ao recuperar usuÃƒÂ¡rio no Discord")
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
     * Envia um arquivo PDF via DM para o usuÃƒÂ¡rio especificado no Discord de forma assÃƒÂ­ncrona.
     * Caso o envio da DM falhe em qualquer etapa da fila assÃƒÂ­ncrona da JDA (erro ao carregar
     * usuÃƒÂ¡rio, erro ao abrir canal privado, ou erro de rede ao enviar o arquivo), executa o
     * callback de fallback fornecido para rotear a mensagem ao canal operacional (webhook).
     *
     * @param discordUserId ID do usuÃƒÂ¡rio alvo no Discord
     * @param pdfBytes      conteÃƒÂºdo binÃƒÂ¡rio do PDF a ser anexado
     * @param filename      nome do arquivo PDF anexado
     * @param message       mensagem de corpo do texto
     * @param fallback      aÃƒÂ§ÃƒÂ£o de contingÃƒÂªncia a ser executada em caso de falha assÃƒÂ­ncrona
     */
    @Async
    public void sendReportPdfDMToUser(String discordUserId, byte[] pdfBytes, String filename, String message, Runnable fallback) {
        if (discordUserId == null || discordUserId.isBlank()) {
            log.info("Ignorando DM de PDF do relatÃƒÂ³rio: usuÃƒÂ¡rio alvo nÃƒÂ£o possui ID do Discord vinculado.");
            if (fallback != null) fallback.run();
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("Ignorando DM de PDF do relatÃƒÂ³rio: JDA nÃƒÂ£o estÃƒÂ¡ disponÃƒÂ­vel no contexto do Spring.");
            if (fallback != null) fallback.run();
            return;
        }

        if (pdfBytes == null || pdfBytes.length == 0 || filename == null || filename.isBlank()) {
            log.warn("Ignorando DM de PDF do relatÃƒÂ³rio: bytes do PDF invÃƒÂ¡lidos ou nome do arquivo vazio.");
            if (fallback != null) fallback.run();
            return;
        }

        // Recupera o usuÃƒÂ¡rio do Discord assincronamente e inicia a abertura do canal de DM privado
        jda.retrieveUserById(discordUserId).queue(
            user -> user.openPrivateChannel().queue(
                channel -> {
                    try {
                        if (message != null && !message.isBlank()) {
                            channel.sendMessage(message).queue();
                        }
                        channel.sendFiles(FileUpload.fromData(pdfBytes, filename)).queue(
                            success -> log.info("DM do PDF do relatÃƒÂ³rio enviada com sucesso para o usuÃƒÂ¡rio {}", discordUserId),
                            error -> {
                                log.warn("Falha assÃƒÂ­ncrona ao enviar DM do PDF do relatÃƒÂ³rio para o usuÃƒÂ¡rio {}", discordUserId, error);
                                if (fallback != null) fallback.run();
                            }
                        );
                    } catch (Exception ex) {
                        log.warn("Erro inesperado ao despachar DM de PDF do relatÃƒÂ³rio para o usuÃƒÂ¡rio {}", discordUserId, ex);
                        if (fallback != null) fallback.run();
                    }
                },
                error -> {
                    log.warn("Falha assÃƒÂ­ncrona ao abrir canal de DM privado para o usuÃƒÂ¡rio {}", discordUserId, error);
                    if (fallback != null) fallback.run();
                }
            ),
            error -> {
                log.warn("Falha assÃƒÂ­ncrona ao recuperar usuÃƒÂ¡rio no Discord via ID {}", discordUserId, error);
                if (fallback != null) fallback.run();
            }
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
     * Envia o cÃƒÂ³digo de recuperaÃƒÂ§ÃƒÂ£o do 2FA ao usuÃƒÂ¡rio via DM no Discord.
     * ExecuÃƒÂ§ÃƒÂ£o sÃƒÂ­ncrona para garantir o retorno do cÃƒÂ³digo antes da resposta da API.
     *
     * @param discordUserId ID do usuÃƒÂ¡rio no Discord
     * @param code          CÃƒÂ³digo de 8 caracteres gerado para a recuperaÃƒÂ§ÃƒÂ£o
     * @param userName      Nome exibido no embed para contextualizar ao usuÃƒÂ¡rio
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
            .setTitle("Ã°Å¸â€Â RecuperaÃƒÂ§ÃƒÂ£o de AutenticaÃƒÂ§ÃƒÂ£o 2FA Ã¢â‚¬â€ Inovare TI")
            .setDescription(
                "OlÃƒÂ¡, **" + userName + "**!\n\n"
                + "Seu cÃƒÂ³digo de recuperaÃƒÂ§ÃƒÂ£o para redefinir o 2FA ÃƒÂ©:\n\n"
                + "```\n" + code + "\n```\n"
                + "Ã¢Å¡Â Ã¯Â¸Â O cÃƒÂ³digo expira em **15 minutos** e ÃƒÂ© de uso ÃƒÂºnico.\n"
                + "Se vocÃƒÂª nÃƒÂ£o solicitou esta recuperaÃƒÂ§ÃƒÂ£o, ignore esta mensagem.")
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
     * Notifica o usuÃƒÂ¡rio quando um administrador reseta o seu 2FA.
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
            .setTitle("Ã°Å¸â€Â Seu 2FA foi resetado")
            .setDescription(
                "OlÃƒÂ¡, **" + targetUserName + "**!\n\n"
                    + "Seu Segundo Fator de AutenticaÃƒÂ§ÃƒÂ£o (2FA) foi resetado por um administrador.\n"
                    + "Por favor, reconfigure-o no seu prÃƒÂ³ximo acesso.\n\n"
                    + "Administrador responsÃƒÂ¡vel: **" + adminName + "**")
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
     * Envia notificaÃƒÂ§ÃƒÂ£o de recibo financeiro via DM ao mÃƒÂ©dico vinculado.
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
            .setTitle("Ã°Å¸â€œâ€ž Recibo Financeiro DisponÃƒÂ­vel")
            .setDescription(
                "OlÃƒÂ¡, **" + medicoNome + "**!\n\n"
                    + "Seu recibo da parcela **" + parcelaId + "** foi processado com sucesso no mÃƒÂ³dulo financeiro.")
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

