package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;

/**
 * Listener de eventos do Discord baseado em JDA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEventListener extends ListenerAdapter {

    private final DiscordUserLinkingService discordUserLinkingService;
    private final DiscordTicketService discordTicketService;

    /**
     * Registra os slash commands globais quando o bot fica pronto.
     */
    @Override
    public void onReady(ReadyEvent event) {
        log.info("✅ Bot do Discord pronto! Registrando slash commands...");

        try {
            var jda = event.getJDA();

            jda.upsertCommand("chamado", "Abre um novo chamado na TI")
                    .addOption(OptionType.STRING, "descricao", "Descrição do problema", true)
                    .addOption(OptionType.STRING, "prioridade", 
                            "Prioridade do chamado (LOW, NORMAL, HIGH, URGENT)", false)
                    .queue(
                            success -> log.info("✅ Slash command '/chamado' registrado com sucesso"),
                            error -> log.error("❌ Falha ao registrar o comando /chamado", error)
                    );

            jda.upsertCommand("vincular", "Vincula sua conta Discord à sua conta da clínica")
                    .addOption(OptionType.STRING, "email", "Seu email de usuário na clínica", true)
                    .queue(
                            success -> log.info("✅ Slash command '/vincular' registrado com sucesso"),
                            error -> log.error("❌ Falha ao registrar o comando /vincular", error)
                    );

            jda.upsertCommand("status", "Verifica o status de um chamado")
                    .addOption(OptionType.STRING, "id_chamado", "ID do chamado (UUID)", true)
                    .queue(
                            success -> log.info("✅ Slash command '/status' registrado com sucesso"),
                            error -> log.error("❌ Falha ao registrar o comando /status", error)
                    );

            jda.upsertCommand("meuschamados", "Lista os seus chamados em andamento na Inovare TI")
                    .queue(
                        success -> log.info("✅ Slash command '/meuschamados' registrado com sucesso"),
                        error -> log.error("❌ Falha ao registrar o comando /meuschamados", error)
                    );

            log.info("✅ Todos os slash commands foram registrados com sucesso!");

        } catch (Exception e) {
            log.error("❌ Erro ao registrar os slash commands", e);
        }
    }

    /**
     * Encaminha as interações de slash command para os handlers.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        log.info("📨 Slash command recebido: /{} do usuário {}", commandName, event.getUser().getAsTag());

        switch (commandName) {
            case "chamado" -> handleChamadoCommand(event);
            case "vincular" -> handleVincularCommand(event);
            case "status" -> handleStatusCommand(event);
            case "meuschamados" -> handleMeusChamadosCommand(event);
            default -> event.reply("❌ Comando desconhecido: " + commandName).setEphemeral(true).queue();
        }
    }

    private void handleChamadoCommand(SlashCommandInteractionEvent event) {
        log.info("📋 Processando o comando /chamado do usuário {}", event.getUser().getId());

        try {
            String discordUserId = event.getUser().getId();
            var descricaoOption = event.getOption("descricao");
            if (descricaoOption == null || descricaoOption.getAsString().isBlank()) {
                event.reply("❌ Informe uma descrição válida.").setEphemeral(true).queue();
                return;
            }

            String descricao = descricaoOption.getAsString();
            var prioridadeOption = event.getOption("prioridade");
            String prioridadeStr = prioridadeOption != null
                    ? prioridadeOption.getAsString().toUpperCase()
                    : "NORMAL";

            String message = discordTicketService.createTicketFromDiscord(discordUserId, descricao, prioridadeStr);
            event.reply(message).queue();

        } catch (Exception e) {
            log.error("❌ Erro ao processar o comando /chamado", e);
            event.reply("❌ Erro ao criar seu chamado. Entre em contato com um administrador.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleVincularCommand(SlashCommandInteractionEvent event) {
        log.info("🔗 Processando o comando /vincular do usuário {}", event.getUser().getId());

        try {
            var emailOption = event.getOption("email");
            if (emailOption == null || emailOption.getAsString().isBlank()) {
                event.reply("❌ Informe um e-mail válido.").setEphemeral(true).queue();
                return;
            }

            String email = emailOption.getAsString().trim();
            String discordUserId = event.getUser().getId();

            log.debug("Tentando vincular o usuário do Discord {} ao e-mail {}", discordUserId, email);

            String message = discordUserLinkingService.linkDiscordToUserAndBuildMessage(email, discordUserId);
            event.reply(message).queue();

        } catch (Exception e) {
            log.error("❌ Erro ao processar o comando /vincular", e);
            event.reply("❌ Erro ao vincular sua conta. Entre em contato com um administrador.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        log.info("🔍 Processando o comando /status do usuário {}", event.getUser().getId());

        try {
            var idOption = event.getOption("id_chamado");
            if (idOption == null || idOption.getAsString().isBlank()) {
                event.reply("❌ Informe um ID de chamado válido.").setEphemeral(true).queue();
                return;
            }

            String ticketIdStr = idOption.getAsString().trim();
            String message = discordTicketService.getTicketStatusFromDiscord(ticketIdStr);
            event.reply(message).queue();

        } catch (Exception e) {
            log.error("❌ Erro ao processar o comando /status", e);
            event.reply("❌ Erro ao consultar o status do chamado. Entre em contato com um administrador.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleMeusChamadosCommand(SlashCommandInteractionEvent event) {
        log.info("📋 Processando o comando /meuschamados do usuário {}", event.getUser().getId());

        try {
            String discordUserId = event.getUser().getId();
            String message = discordTicketService.listMyActiveTicketsFromDiscord(discordUserId);
            event.reply(message).setEphemeral(true).queue();

        } catch (Exception e) {
            log.error("❌ Erro ao processar o comando /meuschamados", e);
            event.reply("❌ Erro ao listar seus chamados. Tente novamente em instantes.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}
