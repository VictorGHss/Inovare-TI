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
            log.warn("Token do bot Discord não configurado. Pulando inicialização do JDA. " +
                "Defina a variável de ambiente DISCORD_BOT_TOKEN para ativar o bot.");
            throw new IllegalArgumentException(
                "O token do bot Discord deve ser configurado via variável de ambiente DISCORD_BOT_TOKEN");
        }

        try {
            log.info("Inicializando JDA com token do bot Discord...");

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
            log.error("❌ Erro ao inicializar bot Discord", e);
            throw new RuntimeException("Falha ao inicializar bot Discord: " + e.getMessage(), e);
        }
    }
}
