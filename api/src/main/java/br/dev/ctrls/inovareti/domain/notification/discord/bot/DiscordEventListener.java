package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;

/**
 * Listener de eventos do Discord usando JDA.
 * 
 * Responsável por:
 * 1. Registrar Slash Commands no servidor Discord (/chamado, /vincular)
 * 2. Processar as interações de Slash Commands
 * 3. Criar tickets no banco de dados quando um usuário usa /chamado
 * 4. Vincular um Discord ID a uma conta de usuário quando usa /vincular
 * 
 * Herda de {@link ListenerAdapter} para sobrescrever apenas os eventos necessários.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEventListener extends ListenerAdapter {

    private final DiscordUserLinkingService discordUserLinkingService;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    /**
     * Chamado quando o bot fica pronto (após conectar ao Discord).
     * Registra globalmente os Slash Commands no servidor Discord.
     *
     * @param event o evento de pronto (ReadyEvent)
     */
    @Override
    public void onReady(ReadyEvent event) {
        log.info("✅ Discord bot is ready! Registering slash commands...");

        try {
            // Obtém o JDA (Java Discord API) a partir do evento
            var jda = event.getJDA();

            // Registra o comando /chamado
            jda.upsertCommand("chamado", "Abre um novo chamado na TI")
                    .addOption(OptionType.STRING, "descricao", "Descrição do problema", true)
                    .addOption(OptionType.STRING, "prioridade", 
                            "Prioridade do chamado (LOW, NORMAL, HIGH, URGENT)", false)
                    .queue(
                            success -> log.info("✅ Slash Command '/chamado' registered successfully"),
                            error -> log.error("❌ Failed to register /chamado command", error)
                    );

            // Registra o comando /vincular
            jda.upsertCommand("vincular", "Vincula sua conta Discord à sua conta da clínica")
                    .addOption(OptionType.STRING, "email", "Seu email de usuário na clínica", true)
                    .queue(
                            success -> log.info("✅ Slash Command '/vincular' registered successfully"),
                            error -> log.error("❌ Failed to register /vincular command", error)
                    );

            // Comando /status para exemplificar (opcional)
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
     * Chamado quando um usuário interage com um Slash Command.
     * Processa os comandos /chamado, /vincular e /status.
     *
     * @param event o evento de interação com slash command
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        log.info("📨 Received slash command: /{} from user {}", commandName, event.getUser().getAsTag());

        // Verifica qual comando foi invocado
        switch (commandName) {
            case "chamado" -> handleComandoChamado(event);
            case "vincular" -> handleComandoVincular(event);
            case "status" -> handleComandoStatus(event);
            default -> event.reply("❌ Comando desconhecido: " + commandName).setEphemeral(true).queue();
        }
    }

    /**
     * Processa o comando /chamado.
     * 
     * Fluxo:
     * 1. Verifica se o Discord ID do usuário está vinculado a uma conta na TI
     * 2. Se não estiver vinculado, responde pedindo para usar /vincular
     * 3. Se estiver vinculado, cria um novo Ticket no banco de dados
     * 4. Responde ao usuário com o ID do novo chamado
     *
     * @param event o evento do slash command /chamado
     */
    private void handleComandoChamado(SlashCommandInteractionEvent event) {
        log.info("📋 Processing /chamado command from user {}", event.getUser().getId());

        // Reconhece a interação para evitar timeout (3 segundos)
        event.deferReply().queue();

        try {
            // Obtém o ID do Discord do usuário
            String discordUserId = event.getUser().getId();
                var descricaoOption = event.getOption("descricao");
                if (descricaoOption == null || descricaoOption.getAsString().isBlank()) {
                event.getHook().editOriginal("❌ Informe uma descrição válida para abrir o chamado.").queue();
                return;
                }

                String descricao = descricaoOption.getAsString();
                var prioridadeOption = event.getOption("prioridade");
                String prioridadeStr = prioridadeOption != null
                    ? prioridadeOption.getAsString().toUpperCase()
                    : "NORMAL";

            log.debug("Creating ticket for Discord user {} with description: {}", 
                    discordUserId, descricao);

            // Verifica se o Discord ID está vinculado a um User
            Optional<User> userOptional = discordUserLinkingService.findUserByDiscordId(discordUserId);

            if (userOptional.isEmpty()) {
                log.warn("⚠️ Discord user {} is not linked to any clinic account", discordUserId);
                event.getHook().editOriginal(
                        "⚠️ Seu Discord não está vinculado à sua conta da clínica. "
                        + "Use o comando `/vincular [seu-email]`."
                ).queue();
                return;
            }

            // Obtém o usuário vinculado
            User requester = userOptional.get();
            log.info("✅ Discord user {} linked to clinic user: {}", discordUserId, requester.getEmail());

            // Obtém uma categoria padrão (pode ser melhorado para aceitar categoria como parâmetro)
            TicketCategory category = ticketCategoryRepository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No ticket category found in database"));

            // Converte a string de prioridade para enum (com fallback)
            TicketPriority priority;
            try {
                priority = TicketPriority.valueOf(prioridadeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid priority '{}'. Using NORMAL.", prioridadeStr);
                priority = TicketPriority.NORMAL;
            }

            // Calcula o SLA deadline (com base na categoria)
            LocalDateTime slaDeadline = LocalDateTime.now(ZoneId.of("UTC"))
                    .plusHours(category.getBaseSlaHours());

            // Cria o novo Ticket
            Ticket newTicket = Ticket.builder()
                    .title("Suporte via Discord") // Título padrão
                    .description(descricao)
                    .status(TicketStatus.OPEN)
                    .priority(priority)
                    .requester(requester)
                    .category(category)
                    .slaDeadline(slaDeadline)
                    .createdAt(LocalDateTime.now(ZoneId.of("UTC")))
                    .build();

            // Salva o ticket no banco de dados
            Ticket savedTicket = ticketRepository.save(newTicket);
            log.info("✅ Ticket created successfully with ID: {}", savedTicket.getId());

            // Formata o ID do ticket para exibição (primeiros 8 caracteres)
            String ticketIdShort = savedTicket.getId().toString().substring(0, 8).toUpperCase();

            // Criando um embed bonito para a resposta
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Chamado Aberto com Sucesso!")
                    .setDescription("Seu chamado foi registrado e a TI foi notificada.")
                    .addField("ID do Chamado", "#" + ticketIdShort, true)
                    .addField("Solicitante", requester.getName(), true)
                    .addField("Prioridade", priority.name(), true)
                    .addField("Descrição", descricao, false)
                    .setColor(0x00FF00) // Verde
                    .setFooter("Inovare TI - Suporte Técnico");

            // Responde com sucesso
            event.getHook().editOriginalEmbeds(embed.build()).queue();

        } catch (Exception e) {
            log.error("❌ Error processing /chamado command", e);
            event.getHook().editOriginal(
                    "❌ Erro ao criar o chamado. Contacte o administrador."
            ).queue();
        }
    }

    /**
     * Processa o comando /vincular.
     * 
     * Fluxo:
     * 1. Obtém o email fornecido pelo comando
     * 2. Busca o usuário no banco de dados pelo email
     * 3. Se encontrado, vincula o Discord ID deste comando ao usuário
     * 4. Responde ao usuário confirmando a vinculação
     *
     * @param event o evento do slash command /vincular
     */
    private void handleComandoVincular(SlashCommandInteractionEvent event) {
        log.info("🔗 Processing /vincular command from user {}", event.getUser().getId());

        // Reconhece a interação para evitar timeout
        event.deferReply(true).queue(); // private reply (ephemeral)

        try {
            // Obtém o email da opção do comando
            var emailOption = event.getOption("email");
            if (emailOption == null || emailOption.getAsString().isBlank()) {
                event.getHook().editOriginal("❌ Informe um email válido para vincular sua conta.").queue();
                return;
            }

            String email = emailOption.getAsString().trim();
            String discordUserId = event.getUser().getId();

            log.debug("Attempting to link Discord user {} to email {}", discordUserId, email);

            // Tenta vincular o Discord ID ao usuário com o email fornecido
            Optional<User> linkedUser = discordUserLinkingService.linkDiscordToUser(email, discordUserId);

            if (linkedUser.isPresent()) {
                User user = linkedUser.get();
                log.info("✅ Successfully linked Discord user {} to clinic user {}",
                        discordUserId, user.getEmail());

                // Cria um embed bonito para a resposta
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("✅ Conta Vinculada com Sucesso!")
                        .setDescription("Seu Discord foi vinculado à sua conta da clínica.")
                        .addField("Nome", user.getName(), true)
                        .addField("Email", user.getEmail(), true)
                        .addField("Setor", user.getSector().getName(), true)
                        .setColor(0x00FF00) // Verde
                        .setFooter("Inovare TI - Suporte Técnico");

                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } else {
                log.warn("⚠️ No user found with email: {}", email);

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("❌ Usuário não encontrado")
                        .setDescription("Nenhum usuário foi encontrado com o email fornecido.")
                        .addField("Email buscado", email, true)
                        .setColor(0xFF0000) // Vermelho
                        .setFooter("Inovare TI - Suporte Técnico");

                event.getHook().editOriginalEmbeds(embed.build()).queue();
            }

        } catch (Exception e) {
            log.error("❌ Error processing /vincular command", e);
            event.getHook().editOriginal(
                    "❌ Erro ao vincular a conta. Contacte o administrador."
            ).queue();
        }
    }

    /**
     * Processa o comando /status.
     * 
     * Fluxo:
     * 1. Busca o ticket pelo ID fornecido
     * 2. Se encontrado, exibe informações do ticket
     * 3. Se não encontrado, responde que o chamado não existe
     *
     * @param event o evento do slash command /status
     */
    private void handleComandoStatus(SlashCommandInteractionEvent event) {
        log.info("🔍 Processing /status command from user {}", event.getUser().getId());

        // Reconhece a interação para evitar timeout
        event.deferReply().queue();

        try {
            // Obtém o ID do chamado da opção do comando
            var idOption = event.getOption("id_chamado");
            if (idOption == null || idOption.getAsString().isBlank()) {
                event.getHook().editOriginal("❌ Informe um ID de chamado válido.").queue();
                return;
            }

            String ticketIdStr = idOption.getAsString().trim();
            
            // Tenta converter para UUID
            UUID ticketId;
            try {
                ticketId = UUID.fromString(ticketIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ticket ID format: {}", ticketIdStr);
                event.getHook().editOriginal(
                        "❌ ID de chamado inválido. Use o formato UUID completo ou os 8 primeiros caracteres."
                ).queue();
                return;
            }

            // Busca o ticket no banco de dados
            Optional<Ticket> ticketOptional = ticketRepository.findById(ticketId);

            if (ticketOptional.isPresent()) {
                Ticket ticket = ticketOptional.get();
                log.info("✅ Found ticket {} with status {}", ticketId, ticket.getStatus());

                // Cria um embed com as informações do ticket
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📋 Status do Chamado #" + ticketId.toString().substring(0, 8).toUpperCase())
                        .addField("Título", ticket.getTitle(), false)
                        .addField("Status", ticket.getStatus().name(), true)
                        .addField("Prioridade", ticket.getPriority().name(), true)
                        .addField("Solicitante", ticket.getRequester().getName(), true)
                        .addField("Descrição", ticket.getDescription() != null ? ticket.getDescription() : "N/A", false)
                        .addField("Criado em", ticket.getCreatedAt().toString(), true)
                        .addField("Deadline SLA", ticket.getSlaDeadline().toString(), true)
                        .setColor(getColorByStatus(ticket.getStatus()))
                        .setFooter("Inovare TI - Suporte Técnico");

                if (ticket.getAssignedTo() != null) {
                    embed.addField("Atribuído a", ticket.getAssignedTo().getName(), true);
                }

                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } else {
                log.warn("❌ Ticket not found with ID: {}", ticketId);
                event.getHook().editOriginal(
                        "❌ Chamado não encontrado. Verifique o ID fornecido."
                ).queue();
            }

        } catch (Exception e) {
            log.error("❌ Error processing /status command", e);
            event.getHook().editOriginal(
                    "❌ Erro ao buscar o status do chamado. Contacte o administrador."
            ).queue();
        }
    }

    /**
     * Retorna a cor do embed com base no status do ticket.
     *
     * @param status o status do ticket
     * @return a cor em formato hexadecimal
     */
    private int getColorByStatus(TicketStatus status) {
        return switch (status) {
            case OPEN -> 0xFF9800;        // Laranja - Aberto
            case IN_PROGRESS -> 0x2196F3; // Azul - Em progresso
            case RESOLVED -> 0x4CAF50;    // Verde - Resolvido
        };
    }
}
