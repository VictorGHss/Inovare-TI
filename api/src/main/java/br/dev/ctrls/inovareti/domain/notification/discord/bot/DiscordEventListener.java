package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Listener de eventos do Discord baseado em JDA.
 * Gerencia slash commands globais do bot.
 *
 * <p>Comandos restritos a técnicos/admin são protegidos com
 * {@link DefaultMemberPermissions#enabledFor(Permission...)} para que não
 * apareçam no autocomplete de usuários comuns (médicos, secretárias).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEventListener extends ListenerAdapter {

    @Value("${discord.bot.guild-id:}")
    private String discordTestGuildId;

    private final DiscordUserLinkingService discordUserLinkingService;
    private final DiscordTicketService discordTicketService;
    private final DiscordCommandService discordCommandService;

    @Qualifier("discordExecutor")
    private final Executor discordExecutor;

    /**
     * Monitora a queda de conexão do bot com o gateway do Discord.
     */
    @Override
    public void onSessionDisconnect(@javax.annotation.Nonnull SessionDisconnectEvent event) {
        String closeCodeInfo = "N/A";
        if (event.getCloseCode() != null) {
            closeCodeInfo = String.format("Código: %d (%s)", event.getCloseCode().getCode(), event.getCloseCode().getMeaning());
        }
        log.warn("⚠️ [DISCORD BOT] Queda detectada na conexão com o Gateway do Discord! Horário: {}, Fechado pelo servidor: {}, Detalhes: {}",
                event.getTimeDisconnected(),
                event.isClosedByServer(),
                closeCodeInfo);
    }

    /**
     * Monitora o restabelecimento da conexão com o gateway do Discord via retomada de sessão (Resume).
     */
    @Override
    public void onSessionResume(@javax.annotation.Nonnull SessionResumeEvent event) {
        log.info("🔄 [DISCORD BOT] Conexão com o Gateway do Discord foi restabelecida com sucesso (Sessão Retomada/Resumed)!");
    }

    /**
     * Monitora o restabelecimento da conexão com o gateway do Discord via recriação de sessão (Recreate).
     */
    @Override
    public void onSessionRecreate(@javax.annotation.Nonnull SessionRecreateEvent event) {
        log.info("🔄 [DISCORD BOT] Conexão com o Gateway do Discord foi restabelecida com sucesso (Sessão Recriada/Recreated)!");
    }

    /**
     * Monitora o encerramento do bot Discord.
     */
    @Override
    public void onShutdown(@javax.annotation.Nonnull ShutdownEvent event) {
        String shutdownInfo = "N/A";
        if (event.getCloseCode() != null) {
            shutdownInfo = String.format("Código: %d (%s)", event.getCloseCode().getCode(), event.getCloseCode().getMeaning());
        }
        log.warn("🛑 [DISCORD BOT] Instância do JDA está sendo destruída/finalizada. Horário: {}, Detalhes: {}",
                event.getTimeShutdown(),
                shutdownInfo);
    }

    /**
     * Registra os slash commands diretamente na guild de teste quando o bot fica pronto.
     */
    @Override
    public void onReady(@javax.annotation.Nonnull ReadyEvent event) {
        log.info("✅ Bot do Discord pronto! Registrando slash commands na guilda...");

        try {
            // Limpa todos os comandos globais para evitar duplicidade de autocomplete no Discord
            event.getJDA().updateCommands().queue();

            net.dv8tion.jda.api.entities.Guild guild = null;
            if (discordTestGuildId != null && !discordTestGuildId.isBlank()) {
                try {
                    long guildId = Long.parseLong(discordTestGuildId.trim());
                    guild = event.getJDA().getGuildById(guildId);
                } catch (NumberFormatException ignored) {}
            }

            if (guild == null) {
                // Fallback resiliente: registra na primeira guilda disponível em que o bot está inserido
                guild = event.getJDA().getGuilds().stream().findFirst().orElse(null);
            }

            if (guild == null) {
                log.warn("⚠️ Nenhuma Guild (Servidor) encontrada. A sincronização imediata dos comandos do Discord foi ignorada.");
                return;
            }

            final net.dv8tion.jda.api.entities.Guild finalGuild = guild;

            log.info("Sincronizando slash commands na guilda: {} (ID: {})", finalGuild.getName(), finalGuild.getId());

            finalGuild.updateCommands()
                .addCommands(
                    Commands.slash("chamado", "Abre um novo chamado na TI")
                        .addOption(OptionType.STRING, "descricao", "Descrição do problema", true)
                        .addOption(OptionType.STRING, "prioridade",
                            "Prioridade do chamado (LOW, NORMAL, HIGH, URGENT)", false)
                        .addOption(OptionType.STRING, "patrimonio", "Código de patrimônio do ativo (ex: INV-2026-00421)", false),

                    Commands.slash("vincular", "Vincula sua conta Discord à sua conta da clínica")
                        .addOption(OptionType.STRING, "email", "Seu email de usuário na clínica", true),

                    Commands.slash("status", "Verifica o status de um chamado")
                        .addOption(OptionType.STRING, "id_chamado", "ID do chamado (UUID)", true),

                    Commands.slash("meuschamados", "Lista os seus chamados em andamento na Inovare TI"),

                    Commands.slash("ajuda", "Busca dúvidas e ajuda no FAQ local da TI")
                        .addOption(OptionType.STRING, "busca", "Palavra-chave ou dúvida para busca no FAQ", true),

                    Commands.slash("ti", "Painel de TI — administração e monitoramento")
                        .addSubcommands(new SubcommandData("status",
                            "Exibe métricas de infraestrutura do servidor (RAM, CPU, Banco)"))
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)),

                    Commands.slash("meusatendimentos",
                        "Lista os chamados em andamento atribuídos a você (TI)")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)),

                    Commands.slash("vincular_afetado",
                        "Vincula um colaborador afetado a um chamado (TI)")
                        .addOption(OptionType.STRING, "id_chamado",
                            "ID do chamado (UUID completo ou 8 primeiros caracteres)", true)
                        .addOption(OptionType.USER, "usuario",
                            "Usuário do Discord a ser vinculado como afetado", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)),

                    Commands.slash("solicitar", "Solicita um item ou insumo ao setor de TI")
                        .addOption(OptionType.STRING, "item",
                            "Nome do item (use o autocomplete para buscar no inventário)",
                            true, true)
                        .addOption(OptionType.INTEGER, "quantidade",
                            "Quantidade desejada (padrão: 1)", false)
                )
                .queue(
                    success -> log.info("✅ Slash commands sincronizados imediatamente na guilda: {}", finalGuild.getName()),
                    error -> log.error("❌ Falha ao sincronizar os slash commands na guilda de teste", error)
                );

        } catch (RuntimeException e) {
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
            case "chamado"           -> handleChamadoCommand(event);
            case "vincular"          -> handleVincularCommand(event);
            case "status"            -> handleStatusCommand(event);
            case "meuschamados"      -> handleMeusChamadosCommand(event);
            case "ajuda"             -> handleAjudaCommand(event);
            case "meusatendimentos"  -> handleMeusAtendimentosCommand(event);
            case "vincular_afetado"  -> handleVincularAfetadoCommand(event);
            default -> log.debug("Comando não tratado no DiscordEventListener: /{}", commandName);
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
        var patrimonioOption = event.getOption("patrimonio");
        String patrimonioStr = patrimonioOption != null ? patrimonioOption.getAsString().trim() : null;

        event.deferReply().queue();

        discordExecutor.execute(() -> {
            try {
                String discordUserId = event.getUser().getId();
                String message = discordTicketService.createTicketFromDiscord(discordUserId, descricao, prioridadeStr, patrimonioStr);
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

        event.deferReply().setEphemeral(true).queue();

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
        event.deferReply().queue();

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

        event.deferReply().setEphemeral(true).queue();

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
        event.deferReply().queue();

        discordExecutor.execute(() -> {
            try {
                List<FaqTi> resultados = discordCommandService.buscarFaq(busca);

                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("🔍 Ajuda e FAQ - Inovare TI");
                embedBuilder.setColor(0x00A2FF);
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

    /**
     * Handler para {@code /meusatendimentos}.
     * Restrito a técnicos/admin. Exibe chamados atribuídos ao técnico com SLA e localização do solicitante.
     */
    private void handleMeusAtendimentosCommand(SlashCommandInteractionEvent event) {
        log.info("🖥️ Processando o comando /meusatendimentos do usuário {}", event.getUser().getId());

        event.deferReply().setEphemeral(true).queue();

        discordExecutor.execute(() -> {
            try {
                String discordUserId = event.getUser().getId();
                // Validação adicional de cargo em runtime (defesa em profundidade)
                var usuario = discordCommandService.resolverTecnico(discordUserId);
                if (usuario == null) {
                    event.getHook().sendMessage(
                            "🔒 Acesso negado. Este comando é restrito a técnicos e administradores de TI."
                    ).queue();
                    return;
                }

                String message = discordCommandService.listarMeusAtendimentos(discordUserId);
                String safeMessage = message != null ? message : "Nenhum atendimento encontrado.";
                event.getHook().sendMessage(safeMessage).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /meusatendimentos", e);
                event.getHook().sendMessage("❌ Erro ao listar atendimentos. Tente novamente em instantes.").queue();
            }
        });
    }

    /**
     * Handler para {@code /vincular_afetado}.
     * Restrito a técnicos/admin. Vincula um colaborador como afetado em um chamado.
     */
    private void handleVincularAfetadoCommand(SlashCommandInteractionEvent event) {
        log.info("🔗 Processando o comando /vincular_afetado do usuário {}", event.getUser().getId());

        var idChamadoOption = event.getOption("id_chamado");
        var usuarioOption   = event.getOption("usuario");

        if (idChamadoOption == null || idChamadoOption.getAsString().isBlank()) {
            event.reply("❌ Informe o ID do chamado.").setEphemeral(true).queue();
            return;
        }

        if (usuarioOption == null) {
            event.reply("❌ Informe o usuário Discord a ser vinculado.").setEphemeral(true).queue();
            return;
        }

        String ticketIdStr      = idChamadoOption.getAsString().trim();
        // Obtém o Discord ID puro do usuário mencionado via OptionType.USER
        String discordIdAfetado = usuarioOption.getAsUser().getId();
        String discordIdTecnico = event.getUser().getId();

        event.deferReply().setEphemeral(true).queue();

        discordExecutor.execute(() -> {
            try {
                // Validação adicional de cargo em runtime (defesa em profundidade)
                var tecnico = discordCommandService.resolverTecnico(discordIdTecnico);
                if (tecnico == null) {
                    event.getHook().sendMessage(
                            "🔒 Acesso negado. Este comando é restrito a técnicos e administradores de TI."
                    ).queue();
                    return;
                }

                String message = discordCommandService.vincularAfetado(discordIdTecnico, ticketIdStr, discordIdAfetado);
                String safeMessage = message != null ? message : "❌ Erro inesperado ao vincular usuário afetado.";
                event.getHook().sendMessage(safeMessage).queue();
            } catch (Exception e) {
                log.error("❌ Erro ao processar o comando /vincular_afetado", e);
                event.getHook().sendMessage("❌ Erro ao vincular usuário afetado. Tente novamente em instantes.").queue();
            }
        });
    }
}
