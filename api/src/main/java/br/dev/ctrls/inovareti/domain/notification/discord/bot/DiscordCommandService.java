package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando do Discord responsável pela lógica de negócios e transações
 * de banco de dados para interações e slash commands do bot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordCommandService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final FaqTiRepository faqTiRepository;

    /**
     * Valida que o usuário Discord é um técnico ou admin vinculado.
     */
    @Transactional(readOnly = true)
    public User resolverTecnico(String discordUserId) {
        User usuario = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (usuario == null || (usuario.getRole() != UserRole.ADMIN && usuario.getRole() != UserRole.TECHNICIAN)) {
            log.warn("[DISCORD] Acesso negado para Discord ID={} — não é técnico/admin vinculado.", discordUserId);
            return null;
        }
        return usuario;
    }

    /**
     * Busca um ticket pelo ID String.
     */
    @Transactional(readOnly = true)
    public Ticket resolverTicket(String ticketIdStr) {
        try {
            UUID ticketId = UUID.fromString(ticketIdStr.trim());
            return ticketRepository.findById(ticketId).orElse(null);
        } catch (IllegalArgumentException ex) {
            log.warn("[DISCORD] UUID inválido fornecido: '{}'", ticketIdStr);
            return null;
        }
    }

    /**
     * Executa a atribuição do chamado para um técnico (ação Assumir chamado).
     */
    @Transactional
    public String assumirChamado(String discordUserId, String ticketIdStr) {
        User tecnico = resolverTecnico(discordUserId);
        if (tecnico == null) {
            return "🔒 Apenas técnicos e administradores de TI podem assumir chamados.";
        }

        Ticket ticket = resolverTicket(ticketIdStr);
        if (ticket == null) {
            return "❌ Chamado não encontrado ou ID inválido.";
        }

        if (ticket.getAssignedTo() != null && !ticket.getAssignedTo().getId().equals(tecnico.getId())) {
            return "⚠️ Este chamado já foi assumido por **" + ticket.getAssignedTo().getName() + "**.";
        }

        ticket.setAssignedTo(tecnico);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket);

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        log.info("[DISCORD] Chamado #{} assumido pelo técnico {} (Discord: {})",
                shortId, tecnico.getName(), discordUserId);

        return "✅ Chamado #" + shortId + " assumido por **" + tecnico.getName() + "**";
    }

    /**
     * Executa a recusa de chamado por um técnico.
     */
    @Transactional(readOnly = true)
    public String recusarChamado(String discordUserId, String ticketIdStr) {
        User tecnico = resolverTecnico(discordUserId);
        if (tecnico == null) {
            return "🔒 Apenas técnicos e administradores de TI podem recusar chamados.";
        }

        Ticket ticket = resolverTicket(ticketIdStr);
        if (ticket == null) {
            return "❌ Chamado não encontrado ou ID inválido.";
        }

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        log.info("[DISCORD] Chamado #{} recusado pelo técnico {} (Discord: {})",
                shortId, tecnico.getName(), discordUserId);

        return "❌ Chamado #" + shortId + " recusado por **" + tecnico.getName() + "**. Aguardando outro técnico.";
    }

    /**
     * Realiza busca na tabela de FAQ local da TI baseada em cláusula LIKE.
     */
    @Transactional(readOnly = true)
    public List<FaqTi> buscarFaq(String busca) {
        if (busca == null) return List.of();
        return faqTiRepository.searchFaq(busca.trim());
    }
}
