package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Configuração do bot Discord usando JDA (Java Discord API).
 * Inicializa o JDABuilder com o token do bot e registra os listeners de eventos.
 *
 * Utiliza @ConditionalOnProperty para permitir desativar o bot se necessário.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true", matchIfMissing = true)
public class DiscordBotConfig {

    @Value("${discord.bot.token:}")
    private String discordBotToken;

    private final DiscordEventListener eventListener;

    /**
     * Cria e inicializa o JDA Builder com o token do bot.
     * Registra o listener de eventos para processar slash commands e outros eventos.
     *
     * @return instância configurada do JDA
     * @throws IllegalArgumentException se o token do bot não estiver configurado
     */
    @Bean
    @ConditionalOnProperty(name = "discord.bot.token")
    public JDA jda() {
        // Validação do token
        if (discordBotToken == null || discordBotToken.isBlank()) {
            log.warn("Discord bot token not configured. Skipping JDA initialization. " +
                    "Set DISCORD_BOT_TOKEN environment variable to enable the bot.");
            throw new IllegalArgumentException(
                    "Discord bot token must be configured via DISCORD_BOT_TOKEN environment variable");
        }

        try {
            log.info("Initializing JDA with Discord bot token...");

            JDA jda = JDABuilder.createDefault(discordBotToken)
                    // Gateway intents necessários para o bot funcionar
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.GUILD_VOICE_STATES
                    )
                    // Define a atividade do bot
                    .setActivity(Activity.playing("Suporte de TI | /chamado"))
                    // Registra o listener de eventos
                    .addEventListeners(eventListener)
                    // Constrói sem bloquear — conexão ocorre em background; ReadyEvent é disparado ao DiscordEventListener
                    .build();

            log.info("JDA iniciado de forma assíncrona. Bot será marcado como pronto ao receber ReadyEvent.");
            return jda;
        } catch (Exception e) {
            log.error("❌ Error initializing Discord bot", e);
            throw new RuntimeException("Failed to initialize Discord bot: " + e.getMessage(), e);
        }
    }
}
