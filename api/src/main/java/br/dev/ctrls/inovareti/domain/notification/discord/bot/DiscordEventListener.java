package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;

/**
 * Discord event listener based on JDA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEventListener extends ListenerAdapter {

    private final DiscordUserLinkingService discordUserLinkingService;
    private final DiscordTicketService discordTicketService;

    /**
     * Registers global slash commands when the bot is ready.
     */
    @Override
    public void onReady(ReadyEvent event) {
        log.info("✅ Discord bot is ready! Registering slash commands...");

        try {
            var jda = event.getJDA();

            jda.upsertCommand("chamado", "Abre um novo chamado na TI")
                    .addOption(OptionType.STRING, "descricao", "Descrição do problema", true)
                    .addOption(OptionType.STRING, "prioridade", 
                            "Prioridade do chamado (LOW, NORMAL, HIGH, URGENT)", false)
                    .queue(
                            success -> log.info("✅ Slash Command '/chamado' registered successfully"),
                            error -> log.error("❌ Failed to register /chamado command", error)
                    );

            jda.upsertCommand("vincular", "Vincula sua conta Discord à sua conta da clínica")
                    .addOption(OptionType.STRING, "email", "Seu email de usuário na clínica", true)
                    .queue(
                            success -> log.info("✅ Slash Command '/vincular' registered successfully"),
                            error -> log.error("❌ Failed to register /vincular command", error)
                    );

            jda.upsertCommand("status", "Verifica o status de um chamado")
                    .addOption(OptionType.STRING, "id_chamado", "ID do chamado (UUID)", true)
                    .queue(
                            success -> log.info("✅ Slash Command '/status' registered successfully"),
                            error -> log.error("❌ Failed to register /status command", error)
                    );

            log.info("✅ All slash commands registered successfully!");

        } catch (Exception e) {
            log.error("❌ Error registering slash commands", e);
        }
    }

    /**
     * Routes slash command interactions to handlers.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        log.info("📨 Received slash command: /{} from user {}", commandName, event.getUser().getAsTag());

        switch (commandName) {
            case "chamado" -> handleChamadoCommand(event);
            case "vincular" -> handleVincularCommand(event);
            case "status" -> handleStatusCommand(event);
            default -> event.reply("❌ Comando desconhecido: " + commandName).setEphemeral(true).queue();
        }
    }

    private void handleChamadoCommand(SlashCommandInteractionEvent event) {
        log.info("📋 Processing /chamado command from user {}", event.getUser().getId());

        try {
            String discordUserId = event.getUser().getId();
            var descricaoOption = event.getOption("descricao");
            if (descricaoOption == null || descricaoOption.getAsString().isBlank()) {
                event.reply("❌ Please provide a valid description.").setEphemeral(true).queue();
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
            log.error("❌ Error processing /chamado command", e);
            event.reply("❌ Error while creating your ticket. Please contact an administrator.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleVincularCommand(SlashCommandInteractionEvent event) {
        log.info("🔗 Processing /vincular command from user {}", event.getUser().getId());

        try {
            var emailOption = event.getOption("email");
            if (emailOption == null || emailOption.getAsString().isBlank()) {
                event.reply("❌ Please provide a valid email.").setEphemeral(true).queue();
                return;
            }

            String email = emailOption.getAsString().trim();
            String discordUserId = event.getUser().getId();

            log.debug("Attempting to link Discord user {} to email {}", discordUserId, email);

            String message = discordUserLinkingService.linkDiscordToUserAndBuildMessage(email, discordUserId);
            event.reply(message).queue();

        } catch (Exception e) {
            log.error("❌ Error processing /vincular command", e);
            event.reply("❌ Error while linking your account. Please contact an administrator.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        log.info("🔍 Processing /status command from user {}", event.getUser().getId());

        try {
            var idOption = event.getOption("id_chamado");
            if (idOption == null || idOption.getAsString().isBlank()) {
                event.reply("❌ Please provide a valid ticket ID.").setEphemeral(true).queue();
                return;
            }

            String ticketIdStr = idOption.getAsString().trim();
            String message = discordTicketService.getTicketStatusFromDiscord(ticketIdStr);
            event.reply(message).queue();

        } catch (Exception e) {
            log.error("❌ Error processing /status command", e);
            event.reply("❌ Error while retrieving ticket status. Please contact an administrator.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}
