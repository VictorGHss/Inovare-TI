package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * Listener JDA responsável pelos eventos interativos do bot Discord:
 *
 * <ul>
 *   <li><b>/ti status</b> — exibe métricas de infraestrutura do servidor</li>
 *   <li><b>/solicitar</b> — cria chamado de solicitação de insumo com autocomplete de itens</li>
 *   <li><b>Botão ticket_accept:{id}</b> — técnico assume o chamado via clique no botão</li>
 *   <li><b>Botão ticket_reject:{id}</b> — técnico recusa o chamado via clique no botão</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordInteractionListener extends ListenerAdapter {

    /** Prefixo do Custom ID para o botão "Assumir chamado". */
    public static final String BOTAO_ASSUMIR_PREFIX = "ticket_accept:";

    /** Prefixo do Custom ID para o botão "Recusar chamado". */
    public static final String BOTAO_RECUSAR_PREFIX = "ticket_reject:";

    private final DiscordInfraStatusService infraStatusService;
    private final DiscordSolicitarService solicitarService;
    private final DiscordCommandService discordCommandService;
    
    @Qualifier("discordExecutor")
    private final Executor discordExecutor;

    // ─────────────────────────────────────────────────────────────────────────
    // SLASH COMMANDS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
        String nome = event.getName();

        switch (nome) {
            case "ti"        -> handleTiStatus(event);
            case "solicitar" -> handleSolicitar(event);
        }
    }

    private void handleTiStatus(SlashCommandInteractionEvent event) {
        log.info("[DISCORD][/ti status] Solicitado por: {} ({})",
                event.getUser().getAsTag(), event.getUser().getId());

        // Adia a resposta imediatamente
        event.deferReply().setEphemeral(true).queue();

        String discordId = event.getUser().getId();

        // Processamento assíncrono na thread virtual
        discordExecutor.execute(() -> {
            try {
                User usuario = discordCommandService.resolverTecnico(discordId);
                if (usuario == null) {
                    event.getHook().sendMessage("🔒 Acesso negado. Este comando é restrito a técnicos e administradores de TI.").queue();
                    return;
                }

                MessageEmbed embed = Objects.requireNonNull(
                        infraStatusService.construirEmbedStatus(),
                        "construirEmbedStatus() retornou null");
                event.getHook().sendMessageEmbeds(embed).queue(
                        ok  -> log.info("[DISCORD][/ti status] Embed enviado com sucesso para {}", discordId),
                        err -> log.warn("[DISCORD][/ti status] Falha ao enviar embed: {}", err.getMessage())
                );
            } catch (Exception ex) {
                log.error("[DISCORD][/ti status] Erro inesperado ao processar comando: {}", ex.getMessage(), ex);
                event.getHook().sendMessage("❌ Erro ao coletar métricas de infraestrutura. Verifique os logs do servidor.").queue();
            }
        });
    }

    private void handleSolicitar(SlashCommandInteractionEvent event) {
        log.info("[DISCORD][/solicitar] Acionado por: {} ({})",
                event.getUser().getAsTag(), event.getUser().getId());

        var opcaoItem = event.getOption("item");
        var opcaoQtd  = event.getOption("quantidade");

        if (opcaoItem == null || opcaoItem.getAsString().isBlank()) {
            event.reply("❌ Informe o item a ser solicitado.").setEphemeral(true).queue();
            return;
        }

        int quantidade = (opcaoQtd != null) ? Math.max(1, opcaoQtd.getAsInt()) : 1;
        String discordUserId  = event.getUser().getId();
        String itemSelecionado = opcaoItem.getAsString();

        // Adia a resposta imediatamente
        event.deferReply().queue();

        // Processamento assíncrono na thread virtual
        discordExecutor.execute(() -> {
            try {
                String resposta = solicitarService.criarTicketDeSolicitacao(
                        discordUserId, itemSelecionado, quantidade);
                if (resposta == null) {
                    resposta = "\u274c Erro inesperado ao registrar sua solicitação.";
                }
                event.getHook().sendMessage(resposta).queue();
            } catch (Exception ex) {
                log.error("[DISCORD][/solicitar] Erro ao criar chamado de solicitação: {}", ex.getMessage(), ex);
                event.getHook().sendMessage("❌ Erro ao registrar sua solicitação. Tente novamente ou contate a TI.").queue();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTOCOMPLETE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("null")
    public void onCommandAutoCompleteInteraction(
            @javax.annotation.Nonnull CommandAutoCompleteInteractionEvent event) {

        if (!"solicitar".equals(event.getName()) || !"item".equals(event.getFocusedOption().getName())) {
            return;
        }

        String textoDigitado = Objects.requireNonNull(event.getFocusedOption().getValue(),
            "textoDigitado");
        log.debug("[DISCORD][autocomplete] '/solicitar item' — filtro: '{}'", textoDigitado);

        // Autocomplete deve responder muito rápido, então roda síncrono ou assíncrono leve.
        // Como o JDA tem timeout curto para autocomplete, rodar diretamente é o padrão na API do Discord.
        try {
            List<Command.Choice> opcoes = List.copyOf(
                    solicitarService.buscarOpcoesAutocomplete(textoDigitado));
            event.replyChoices(opcoes).queue();
        } catch (Exception ex) {
            log.warn("[DISCORD][autocomplete] Erro ao buscar opções: {}", ex.getMessage());
            event.replyChoices(
                    new Command.Choice(
                            DiscordSolicitarService.ITEM_FORA_DE_ESTOQUE_ID,
                            "Outros / Fora de Estoque"))
                    .queue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BOTÕES
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(@javax.annotation.Nonnull ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        log.info("[DISCORD][botão] Clique recebido. CustomId='{}', Usuário='{}'",
                customId, event.getUser().getAsTag());

        // Adia a edição imediatamente
        event.deferEdit().queue();

        // Processa lógica pesada assincronamente na thread virtual
        discordExecutor.execute(() -> {
            if (customId.startsWith(BOTAO_ASSUMIR_PREFIX)) {
                handleBotaoAssumir(event, customId.substring(BOTAO_ASSUMIR_PREFIX.length()));
            } else if (customId.startsWith(BOTAO_RECUSAR_PREFIX)) {
                handleBotaoRecusar(event, customId.substring(BOTAO_RECUSAR_PREFIX.length()));
            }
        });
    }

    private void handleBotaoAssumir(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        String resultMessage = discordCommandService.assumirChamado(discordUserId, ticketIdStr);

        if (resultMessage.startsWith("🔒") || resultMessage.startsWith("❌") || resultMessage.startsWith("⚠️")) {
            event.getHook().sendMessage(resultMessage).setEphemeral(true).queue();
            return;
        }

        desabilitarBotoesDaMensagem(event, resultMessage);
    }

    private void handleBotaoRecusar(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        String resultMessage = discordCommandService.recusarChamado(discordUserId, ticketIdStr);

        if (resultMessage.startsWith("🔒") || resultMessage.startsWith("❌")) {
            event.getHook().sendMessage(resultMessage).setEphemeral(true).queue();
            return;
        }

        desabilitarBotoesDaMensagem(event, resultMessage);
    }

    @SuppressWarnings("null")
    private void desabilitarBotoesDaMensagem(ButtonInteractionEvent event, String rodape) {
        List<Button> botoesDessa = List.copyOf(
                event.getMessage().getButtons().stream()
                        .map(Button::asDisabled)
                        .toList());

        event.getHook().editOriginalComponents(ActionRow.of(botoesDessa))
                .setContent(event.getMessage().getContentRaw() + "\n\n" + rodape)
                .queue(
                        ok  -> log.info("[DISCORD][botão] Mensagem editada com botões desabilitados. Rodapé: '{}'", rodape),
                        err -> log.warn("[DISCORD][botão] Falha ao editar mensagem: {}", err.getMessage())
                );
    }

    public static ActionRow criarBotoesDeAcao(UUID ticketId) {
        String idStr = ticketId.toString();
        Button botaoAssumir = Button.primary(BOTAO_ASSUMIR_PREFIX + idStr, "✅ Assumir");
        Button botaoRecusar = Button.danger(BOTAO_RECUSAR_PREFIX + idStr, "❌ Recusar");
        return ActionRow.of(botaoAssumir, botaoRecusar);
    }
}
