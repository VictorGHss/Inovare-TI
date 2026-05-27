package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Listener de eventos do Discord baseado em JDA.
 * Gerencia slash commands globais do bot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEventListener extends ListenerAdapter {

    private final DiscordUserLinkingService discordUserLinkingService;
    private final DiscordTicketService discordTicketService;
    private final DiscordCommandService discordCommandService;

    @Qualifier("discordExecutor")
    private final Executor discordExecutor;

    /**
     * Registra os slash commands globais quando o bot fica pronto.
     */
    @Override
    public void onReady(@javax.annotation.Nonnull ReadyEvent event) {
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

            // Comando /ajuda para consulta no FAQ
            jda.upsertCommand("ajuda", "Busca dúvidas e ajuda no FAQ local da TI")
                    .addOption(OptionType.STRING, "busca", "Palavra-chave ou dúvida para busca no FAQ", true)
                    .queue(
                            success -> log.info("✅ Slash command '/ajuda' registrado com sucesso"),
                            error -> log.error("❌ Falha ao registrar o comando /ajuda", error)
                    );

            // Comando /ti com subcomando status
            jda.upsertCommand(
                            Commands.slash("ti", "Painel de TI — administração e monitoramento")
                                     .addSubcommands(new SubcommandData("status",
                                             "Exibe métricas de infraestrutura do servidor (RAM, CPU, Banco)")))
                    .queue(
                            ok    -> log.info("✅ Slash command '/ti status' registrado com sucesso"),
                            error -> log.error("❌ Falha ao registrar o comando /ti", error)
                    );

            // Comando /solicitar com autocomplete de item
            jda.upsertCommand(
                            Commands.slash("solicitar", "Solicita um item ou insumo ao setor de TI")
                                    .addOption(OptionType.STRING, "item",
                                            "Nome do item (use o autocomplete para buscar no inventário)",
                                            true, true)  // obrigatório=true, autocomplete=true
                                    .addOption(OptionType.INTEGER, "quantidade",
                                            "Quantidade desejada (padrão: 1)", false))
                    .queue(
                            ok    -> log.info("✅ Slash command '/solicitar' registrado com sucesso"),
                            error -> log.error("❌ Falha ao registrar o comando /solicitar", error)
                    );

            log.info("✅ Todos os slash commands foram registrados globalmente!");

            // Limpa os comandos locais das guildas para evitar duplicação com os globais
            for (var guild : jda.getGuilds()) {
                guild.updateCommands().queue(
                        success -> log.info("✅ Comandos locais purgados na guilda: {} para evitar duplicidade", guild.getName()),
                        error -> log.warn("⚠️ Não foi possível purgar comandos locais na guilda: {}", guild.getName())
                );
            }

        } catch (Exception e) {
            log.error("❌ Erro ao registrar os slash commands", e);
        }
    }

    /**
     * Encaminha as interações de slash command para os handlers assíncronos.
     */
    @Override
    public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        log.info("📨 Slash command recebido: /{} do usuário {}", commandName, event.getUser().getAsTag());

        switch (commandName) {
            case "chamado"      -> handleChamadoCommand(event);
            case "vincular"     -> handleVincularCommand(event);
            case "status"       -> handleStatusCommand(event);
            case "meuschamados" -> handleMeusChamadosCommand(event);
            case "ajuda"        -> handleAjudaCommand(event);
            default             -> log.debug("Comando não tratado no DiscordEventListener: /{}", commandName);
        }
    }

    private void handleChamadoCommand(SlashCommandInteractionEvent event) {
        log.info("📋 Processando o comando /chamado do usuário {}", event.getUser().getId());

        var descricaoOption = event.getOption("descricao");
        if (descricaoOption == null || descricaoOption.getAsString().isBlank()) {
            event.reply("❌ Informe uma descrição válida.").setEphemeral(true).queue();
            return;
        }

        String descricao = descricaoOption.getAsString();
        var prioridadeOption = event.getOption("prioridade");
        String prioridadeStr = prioridadeOption != null ? prioridadeOption.getAsString().toUpperCase() : "NORMAL";

        // Adia a resposta
        event.deferReply().queue();

        // Processa de forma assíncrona na thread virtual
        discordExecutor.execute(() -> {
            try {
                String discordUserId = event.getUser().getId();
                String message = discordTicketService.createTicketFromDiscord(discordUserId, descricao, prioridadeStr);
                String safeMessage = message != null ? message : "❌ Erro ao criar seu chamado. Entre em contato com um administrador.";
                event.getHook().sendMessage(safeMessage).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /chamado", e);
                event.getHook().sendMessage("❌ Erro ao criar seu chamado. Entre em contato com um administrador.").queue();
            }
        });
    }

    private void handleVincularCommand(SlashCommandInteractionEvent event) {
        log.info("🔗 Processando o comando /vincular do usuário {}", event.getUser().getId());

        var emailOption = event.getOption("email");
        if (emailOption == null || emailOption.getAsString().isBlank()) {
            event.reply("❌ Informe um e-mail válido.").setEphemeral(true).queue();
            return;
        }

        String email = emailOption.getAsString().trim();
        String discordUserId = event.getUser().getId();

        // Adia a resposta como efêmera
        event.deferReply().setEphemeral(true).queue();

        // Processa de forma assíncrona na thread virtual
        discordExecutor.execute(() -> {
            try {
                log.debug("Tentando vincular o usuário do Discord {} ao e-mail {}", discordUserId, email);
                String message = discordUserLinkingService.linkDiscordToUserAndBuildMessage(email, discordUserId);
                String safeMessage = message != null ? message : "❌ Erro ao vincular sua conta. Entre em contato com um administrador.";
                event.getHook().sendMessage(safeMessage).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /vincular", e);
                event.getHook().sendMessage("❌ Erro ao vincular sua conta. Entre em contato com um administrador.").queue();
            }
        });
    }

    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        log.info("🔍 Processando o comando /status do usuário {}", event.getUser().getId());

        var idOption = event.getOption("id_chamado");
        if (idOption == null || idOption.getAsString().isBlank()) {
            event.reply("❌ Informe um ID de chamado válido.").setEphemeral(true).queue();
            return;
        }

        String ticketIdStr = idOption.getAsString().trim();

        // Adia a resposta
        event.deferReply().queue();

        // Processa de forma assíncrona na thread virtual
        discordExecutor.execute(() -> {
            try {
                String message = discordTicketService.getTicketStatusFromDiscord(ticketIdStr);
                String safeMessage = message != null ? message : "❌ Erro ao consultar o status do chamado. Entre em contato com um administrador.";
                event.getHook().sendMessage(safeMessage).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /status", e);
                event.getHook().sendMessage("❌ Erro ao consultar o status do chamado. Entre em contato com um administrador.").queue();
            }
        });
    }

    private void handleMeusChamadosCommand(SlashCommandInteractionEvent event) {
        log.info("📋 Processando o comando /meuschamados do usuário {}", event.getUser().getId());

        // Adia a resposta como efêmera
        event.deferReply().setEphemeral(true).queue();

        // Processa de forma assíncrona na thread virtual
        discordExecutor.execute(() -> {
            try {
                String discordUserId = event.getUser().getId();
                String message = discordTicketService.listMyActiveTicketsFromDiscord(discordUserId);
                String safeMessage = message != null ? message : "Nenhum chamado encontrado.";
                event.getHook().sendMessage(safeMessage).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /meuschamados", e);
                event.getHook().sendMessage("❌ Erro ao listar seus chamados. Tente novamente em instantes.").queue();
            }
        });
    }

    private void handleAjudaCommand(SlashCommandInteractionEvent event) {
        log.info("ℹ️ Processando o comando /ajuda do usuário {}", event.getUser().getId());

        var buscaOption = event.getOption("busca");
        if (buscaOption == null || buscaOption.getAsString().isBlank()) {
            event.reply("❌ Informe uma dúvida ou termo para a pesquisa.").setEphemeral(true).queue();
            return;
        }

        String busca = buscaOption.getAsString().trim();

        // Adia a resposta
        event.deferReply().queue();

        // Processa de forma assíncrona na thread virtual
        discordExecutor.execute(() -> {
            try {
                List<FaqTi> resultados = discordCommandService.buscarFaq(busca);

                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("🔍 Ajuda e FAQ - Inovare TI");
                embedBuilder.setColor(0x00A2FF); // Azul Inovare
                embedBuilder.setDescription("Resultados da busca para: *" + busca + "*\n\n");

                if (resultados.isEmpty()) {
                    String descricaoSemResultados = """
                            ❌ Nenhuma dúvida correspondente encontrada no FAQ local.
                            Por favor, tente outras palavras-chave ou abra um chamado técnico usando o comando `/chamado`.
                            """.strip();
                    if (descricaoSemResultados != null) {
                        embedBuilder.appendDescription(descricaoSemResultados);
                    }
                } else {
                    for (FaqTi faq : resultados) {
                        embedBuilder.addField("❓ " + faq.getPergunta(), "💡 " + faq.getResposta(), false);
                    }
                    embedBuilder.setFooter("Inovare TI • Sistema de Automação de Suporte", event.getJDA().getSelfUser().getAvatarUrl());
                }

                event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /ajuda", e);
                event.getHook().sendMessage("❌ Erro ao processar sua solicitação de ajuda. Tente novamente mais tarde.").queue();
            }
        });
    }
}
