package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Serviço responsável pela lógica de negócio do comando Discord '/solicitar'.
 *
 * <p>Responsabilidades:
 * <ol>
 *   <li>Servir opções de autocomplete buscando itens no inventário por nome</li>
 *   <li>Criar um Ticket de solicitação de insumo quando o usuário confirmar o comando</li>
 * </ol>
 *
 * <p>A opção estática "Outros / Fora de Estoque" é sempre injetada no topo da lista
 * de autocomplete para permitir solicitações de itens não cadastrados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordSolicitarService {

    /** Valor especial enviado quando o usuário escolhe a opção estática. */
    public static final String ITEM_FORA_DE_ESTOQUE_ID = "OUTROS_FORA_ESTOQUE";

    /** Label exibida no autocomplete para itens fora do inventário. */
    private static final String LABEL_FORA_ESTOQUE = "Outros / Fora de Estoque";

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    /**
     * Monta as opções de autocomplete para o parâmetro 'item' do comando /solicitar.
     *
     * <p>Regras:
     * <ul>
     *   <li>A opção "Outros / Fora de Estoque" é sempre injetada como primeira opção</li>
     *   <li>Em seguida, até 24 itens do banco que contenham o texto digitado (case-insensitive)</li>
     *   <li>Total máximo: 25 opções (limite da API do Discord)</li>
     * </ul>
     *
     * @param textoDigitado fragmento de nome digitado pelo usuário
     * @return lista de {@link Command.Choice} para a resposta de autocomplete
     */
    public List<Command.Choice> buscarOpcoesAutocomplete(String textoDigitado) {
        List<Command.Choice> opcoes = new ArrayList<>();

        // Opção estática obrigatória sempre no topo
        opcoes.add(new Command.Choice(LABEL_FORA_ESTOQUE, ITEM_FORA_DE_ESTOQUE_ID));

        // Busca até 25 itens; como a opção estática já ocupa 1 slot, limitamos a busca a 25
        // e a lista final ficará com no máximo 25 (1 estática + 24 do banco, ou menos)
        String filtro = (textoDigitado != null && !textoDigitado.isBlank()) ? textoDigitado : "";
        List<Item> itensBanco = itemRepository.findTop25ByNameContainingIgnoreCase(filtro);

        for (Item item : itensBanco) {
            if (opcoes.size() >= 25) break;
            // O valor enviado ao Discord é o UUID do item para recuperação posterior
            String nomeLabel  = item.getName() + " (estoque: " + item.getCurrentStock() + ")";
            String idValor    = item.getId().toString();
            opcoes.add(new Command.Choice(nomeLabel, idValor));
        }

        log.debug("[DISCORD][/solicitar] Autocomplete gerado com {} opções para o filtro '{}'",
                opcoes.size(), filtro);
        return opcoes;
    }

    /**
     * Cria um Ticket de solicitação de insumo a partir do comando /solicitar.
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Verifica se o Discord ID está vinculado a um usuário do sistema</li>
     *   <li>Se o valor for o ID especial {@value #ITEM_FORA_DE_ESTOQUE_ID}, cria uma
     *       solicitação genérica sem vincular ao estoque</li>
     *   <li>Se for um UUID válido, busca o item no banco e associa ao chamado</li>
     *   <li>Persiste o ticket com status OPEN e prioridade NORMAL</li>
     * </ol>
     *
     * @param discordUserId  ID do usuário no Discord que executou o comando
     * @param itemSelecionado valor selecionado no autocomplete (UUID do item ou a constante especial)
     * @param quantidade      quantidade desejada (informada no comando)
     * @return mensagem de confirmação ou erro a ser exibida ao usuário
     */
    @Transactional
    public String criarTicketDeSolicitacao(String discordUserId, String itemSelecionado, int quantidade) {
        // Valida vínculo Discord ↔ usuário do sistema
        User solicitante = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (solicitante == null) {
            return "⚠️ Seu Discord não está vinculado à sua conta da clínica. Use `/vincular [seu-email]` primeiro.";
        }

        // Obtém a categoria padrão de chamados
        TicketCategory categoria = ticketCategoryRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhuma categoria de chamado cadastrada no sistema."));

        LocalDateTime agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        String titulo;
        String descricao;
        Item itemEstoque = null;

        if (ITEM_FORA_DE_ESTOQUE_ID.equals(itemSelecionado)) {
            // Solicitação genérica: item não cadastrado no inventário
            titulo = "[DISCORD] Solicitação: Outros / Fora de Estoque";
            descricao = "[DISCORD] O usuário " + solicitante.getName()
                    + " solicitou " + quantidade + " unidade(s) de um item não cadastrado no inventário."
                    + " Por favor, entrar em contato para detalhamento.";
        } else {
            // Tenta resolver o UUID do item
            try {
                UUID itemId = UUID.fromString(itemSelecionado);
                itemEstoque = itemRepository.findById(itemId).orElse(null);
            } catch (IllegalArgumentException ex) {
                log.warn("[DISCORD][/solicitar] Valor inválido para itemSelecionado: '{}'", itemSelecionado);
            }

            if (itemEstoque == null) {
                return "❌ Item não encontrado no inventário. Tente novamente ou escolha 'Outros / Fora de Estoque'.";
            }

            titulo = "[DISCORD] Solicitação: " + itemEstoque.getName();
            descricao = "[DISCORD] O usuário " + solicitante.getName()
                    + " solicitou " + quantidade + " unidade(s) de **" + itemEstoque.getName()
                    + "** (estoque atual: " + itemEstoque.getCurrentStock() + ").";
        }

        Ticket ticket = Ticket.builder()
                .title(titulo.length() > 150 ? titulo.substring(0, 147) + "..." : titulo)
                .description(descricao)
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.NORMAL)
                .requester(solicitante)
                .category(categoria)
                .requestedItem(itemEstoque)
                .requestedQuantity(itemEstoque != null ? quantidade : null)
                .slaDeadline(agora.plusHours(categoria.getBaseSlaHours()))
                .createdAt(agora)
                .build();

        Ticket salvo = ticketRepository.save(ticket);
        String shortId = salvo.getId().toString().substring(0, 8).toUpperCase();

        log.info("[DISCORD][/solicitar] Chamado #{} criado pelo usuário {} (Discord: {}) — item: {}",
                shortId, solicitante.getName(), discordUserId, itemSelecionado);

        return "✅ Solicitação **#" + shortId + "** registrada com sucesso! A TI foi notificada e irá analisar seu pedido.";
    }
}
