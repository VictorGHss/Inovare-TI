package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
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
 * Listener JDA responsável pelos novos eventos interativos do bot Discord:
 *
 * <ul>
 *   <li><b>/ti status</b> — exibe métricas de infraestrutura do servidor</li>
 *   <li><b>/solicitar</b> — cria chamado de solicitação de insumo com autocomplete de itens</li>
 *   <li><b>Botão ticket_accept:{id}</b> — técnico assume o chamado via clique no botão</li>
 *   <li><b>Botão ticket_reject:{id}</b> — técnico recusa o chamado via clique no botão</li>
 * </ul>
 *
 * <p>Prefixos de botão:
 * <pre>
 *   ticket_accept:{ticketId}
 *   ticket_reject:{ticketId}
 * </pre>
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
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // SLASH COMMANDS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Roteia eventos de slash command para os handlers específicos.
     * Comandos gerenciados por este listener: /ti (subcomando 'status') e /solicitar.
     */
    @Override
    public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
        String nome = event.getName();

        switch (nome) {
            case "ti"        -> handleTiStatus(event);
            case "solicitar" -> handleSolicitar(event);
            // Demais comandos (/chamado, /vincular, etc.) são tratados pelo DiscordEventListener
        }
    }

    /**
     * Responde ao comando '/ti status' com um embed de métricas de infraestrutura.
     * Restrito a usuários com papéis ADMIN ou TECHNICIAN no sistema (verificação via discordUserId).
     */
    private void handleTiStatus(SlashCommandInteractionEvent event) {
        log.info("[DISCORD][/ti status] Solicitado por: {} ({})",
                event.getUser().getAsTag(), event.getUser().getId());

        // Verifica se o Discord do usuário está vinculado a um ADMIN ou TECHNICIAN
        String discordId = event.getUser().getId();
        User usuario = userRepository.findByDiscordUserId(discordId).orElse(null);

        if (usuario == null || (usuario.getRole() != UserRole.ADMIN && usuario.getRole() != UserRole.TECHNICIAN)) {
            event.reply("🔒 Acesso negado. Este comando é restrito a técnicos e administradores de TI.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            event.deferReply().queue(); // Adia a resposta enquanto coleta métricas (pode levar > 3s)
            MessageEmbed embed = Objects.requireNonNull(
                    infraStatusService.construirEmbedStatus(),
                    "construirEmbedStatus() retornou null");
            event.getHook().sendMessageEmbeds(embed).queue(
                    ok  -> log.info("[DISCORD][/ti status] Embed enviado com sucesso para {}", discordId),
                    err -> log.warn("[DISCORD][/ti status] Falha ao enviar embed: {}", err.getMessage())
            );
        } catch (Exception ex) {
            log.error("[DISCORD][/ti status] Erro inesperado ao processar comando: {}", ex.getMessage(), ex);
            event.reply("❌ Erro ao coletar métricas de infraestrutura. Verifique os logs do servidor.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    /**
     * Processa o comando '/solicitar item:[valor] quantidade:[n]'.
     * Cria um Ticket de solicitação de insumo para o usuário vinculado.
     */
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

        try {
            String resposta = Objects.requireNonNullElse(
                    solicitarService.criarTicketDeSolicitacao(discordUserId, itemSelecionado, quantidade),
                    "\u274c Erro inesperado ao registrar sua solicitação.");
            event.reply(resposta).queue();
        } catch (Exception ex) {
            log.error("[DISCORD][/solicitar] Erro ao criar chamado de solicitação: {}", ex.getMessage(), ex);
            event.reply("❌ Erro ao registrar sua solicitação. Tente novamente ou contate a TI.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTOCOMPLETE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fornece opções de autocomplete para o parâmetro 'item' do comando '/solicitar'.
     * Sempre inclui "Outros / Fora de Estoque" como primeira opção.
     */
    @Override
    public void onCommandAutoCompleteInteraction(
            @javax.annotation.Nonnull CommandAutoCompleteInteractionEvent event) {

        if (!"solicitar".equals(event.getName()) || !"item".equals(event.getFocusedOption().getName())) {
            return;
        }

        String textoDigitado = event.getFocusedOption().getValue();
        log.debug("[DISCORD][autocomplete] '/solicitar item' — filtro: '{}'", textoDigitado);

        try {
            List<Command.Choice> opcoes = List.copyOf(
                    solicitarService.buscarOpcoesAutocomplete(textoDigitado));
            event.replyChoices(opcoes).queue();
        } catch (Exception ex) {
            log.warn("[DISCORD][autocomplete] Erro ao buscar opções: {}", ex.getMessage());
            // Em caso de falha, retorna pelo menos a opção estática
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

    /**
     * Processa cliques nos botões 'Assumir' e 'Recusar' de notificações de chamados.
     *
     * <p>Fluxo do botão Assumir ({@value #BOTAO_ASSUMIR_PREFIX}{ticketId}):
     * <ol>
     *   <li>Verifica se o Discord ID pertence a um ADMIN ou TECHNICIAN vinculado</li>
     *   <li>Carrega o ticket pelo UUID extraído do Custom ID</li>
     *   <li>Atribui o chamado ao técnico, muda o status para IN_PROGRESS</li>
     *   <li>Edita a mensagem original desabilitando todos os botões</li>
     * </ol>
     *
     * <p>Fluxo do botão Recusar ({@value #BOTAO_RECUSAR_PREFIX}{ticketId}):
     * <ol>
     *   <li>Mesma verificação de perfil</li>
     *   <li>Registra a recusa em log e edita a mensagem desabilitando os botões</li>
     * </ol>
     */
    @Override
    @Transactional
    public void onButtonInteraction(@javax.annotation.Nonnull ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        log.info("[DISCORD][botão] Clique recebido. CustomId='{}', Usuário='{}'",
                customId, event.getUser().getAsTag());

        if (customId.startsWith(BOTAO_ASSUMIR_PREFIX)) {
            handleBotaoAssumir(event, customId.substring(BOTAO_ASSUMIR_PREFIX.length()));
        } else if (customId.startsWith(BOTAO_RECUSAR_PREFIX)) {
            handleBotaoRecusar(event, customId.substring(BOTAO_RECUSAR_PREFIX.length()));
        }
    }

    private void handleBotaoAssumir(ButtonInteractionEvent event, String ticketIdStr) {
        // Valida técnico
        User tecnico = resolverTecnico(event);
        if (tecnico == null) return;

        // Carrega o ticket
        Ticket ticket = resolverTicket(event, ticketIdStr);
        if (ticket == null) return;

        // Verifica se já foi assumido por outro técnico
        if (ticket.getAssignedTo() != null && !ticket.getAssignedTo().getId().equals(tecnico.getId())) {
            event.reply("⚠️ Este chamado já foi assumido por **" + ticket.getAssignedTo().getName() + "**.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Atribui e salva
        ticket.setAssignedTo(tecnico);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket);

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        log.info("[DISCORD][botão] Chamado #{} assumido pelo técnico {} (Discord: {})",
                shortId, tecnico.getName(), event.getUser().getId());

        // Edita a mensagem original desabilitando os botões
        desabilitarBotoesDaMensagem(event,
                "✅ Chamado #" + shortId + " assumido por **" + tecnico.getName() + "**");
    }

    private void handleBotaoRecusar(ButtonInteractionEvent event, String ticketIdStr) {
        // Valida técnico
        User tecnico = resolverTecnico(event);
        if (tecnico == null) return;

        // Carrega o ticket
        Ticket ticket = resolverTicket(event, ticketIdStr);
        if (ticket == null) return;

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        log.info("[DISCORD][botão] Chamado #{} recusado pelo técnico {} (Discord: {})",
                shortId, tecnico.getName(), event.getUser().getId());

        // Edita a mensagem original desabilitando os botões
        desabilitarBotoesDaMensagem(event,
                "❌ Chamado #" + shortId + " recusado por **" + tecnico.getName() + "**. Aguardando outro técnico.");
    }

    /**
     * Valida que o usuário Discord que clicou no botão é um técnico ou admin vinculado.
     * Responde com mensagem efêmera de erro e retorna {@code null} se não for elegível.
     */
    private User resolverTecnico(ButtonInteractionEvent event) {
        String discordId = event.getUser().getId();
        User usuario = userRepository.findByDiscordUserId(discordId).orElse(null);

        if (usuario == null || (usuario.getRole() != UserRole.ADMIN && usuario.getRole() != UserRole.TECHNICIAN)) {
            event.reply("🔒 Apenas técnicos e administradores de TI podem assumir ou recusar chamados.")
                    .setEphemeral(true)
                    .queue();
            log.warn("[DISCORD][botão] Acesso negado para Discord ID={} — não é técnico/admin vinculado.", discordId);
            return null;
        }

        return usuario;
    }

    /**
     * Carrega o Ticket pelo UUID extraído do Custom ID do botão.
     * Responde com erro e retorna {@code null} se não encontrado ou UUID inválido.
     */
    private Ticket resolverTicket(ButtonInteractionEvent event, String ticketIdStr) {
        try {
            UUID ticketId = UUID.fromString(ticketIdStr.trim());
            Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
            if (ticket == null) {
                event.reply("❌ Chamado não encontrado (ID: " + ticketIdStr + ").")
                        .setEphemeral(true)
                        .queue();
                log.warn("[DISCORD][botão] Chamado {} não encontrado na base de dados.", ticketIdStr);
                return null;
            }
            return ticket;
        } catch (IllegalArgumentException ex) {
            event.reply("❌ ID de chamado inválido.").setEphemeral(true).queue();
            log.warn("[DISCORD][botão] UUID inválido extraído do customId: '{}'", ticketIdStr);
            return null;
        }
    }

    /**
     * Edita a mensagem original substituindo os botões por versões desabilitadas
     * e adiciona um rodapé com o resultado da ação.
     */
    private void desabilitarBotoesDaMensagem(ButtonInteractionEvent event, String rodape) {
        // Cria versões desabilitadas de todos os botões da ActionRow original
        List<Button> botoesDessa = List.copyOf(
                event.getMessage().getButtons().stream()
                        .map(Button::asDisabled)
                        .toList());

        event.editComponents(ActionRow.of(botoesDessa))
                .setContent(event.getMessage().getContentRaw() + "\n\n" + rodape)
                .queue(
                        ok  -> log.info("[DISCORD][botão] Mensagem editada com botões desabilitados. Rodapé: '{}'", rodape),
                        err -> log.warn("[DISCORD][botão] Falha ao editar mensagem: {}", err.getMessage())
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FÁBRICA DE BOTÕES (usado pelo DiscordWebhookService para montar ActionRows)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cria o {@link ActionRow} com os botões "✅ Assumir" e "❌ Recusar" para
     * notificações de novos chamados enviadas via JDA.
     *
     * @param ticketId UUID completo do chamado
     * @return ActionRow com dois botões — PRIMARY para assumir, DANGER para recusar
     */
    public static ActionRow criarBotoesDeAcao(UUID ticketId) {
        String idStr = ticketId.toString();
        Button botaoAssumir = Button.primary(BOTAO_ASSUMIR_PREFIX + idStr, "✅ Assumir");
        Button botaoRecusar = Button.danger(BOTAO_RECUSAR_PREFIX + idStr, "❌ Recusar");
        return ActionRow.of(botaoAssumir, botaoRecusar);
    }
}
