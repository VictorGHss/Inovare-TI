package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.AddAdditionalUserUseCase;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.FaqTiRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import br.dev.ctrls.inovareti.domain.knowledge.Article;
import br.dev.ctrls.inovareti.domain.knowledge.ArticleRepository;

/**
 * Serviço de comando do Discord responsável pela lógica de negócios e transações
 * de banco de dados para interações e slash commands do bot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordCommandService {

    private final UserRepositoryPort userRepository;
    private final TicketRepositoryPort ticketRepository;
    private final FaqTiRepositoryPort faqTiRepository;
    private final AddAdditionalUserUseCase addAdditionalUserUseCase;
    private final ArticleRepository articleRepository;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public String getFrontendUrl() {
        return this.frontendUrl;
    }

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

    /**
     * Realiza busca textual na Base de Conhecimento (artigos publicados).
     */
    @Transactional(readOnly = true)
    public List<Article> buscarArtigos(String busca) {
        if (busca == null || busca.trim().isEmpty()) {
            return List.of();
        }
        return articleRepository.findPublishedByTitleOrContentContainingIgnoreCase(busca.trim());
    }

    /**
     * Vincula um usuário adicional afetado a um chamado.
     * Restrito a técnicos e administradores.
     *
     * @param discordIdTecnico  Discord ID do técnico que executa a ação
     * @param ticketIdStr       ID (completo ou curto) do chamado
     * @param discordIdAfetado  Discord ID do usuário afetado a ser vinculado
     */
    @Transactional
    public String vincularAfetado(String discordIdTecnico, String ticketIdStr, String discordIdAfetado) {
        User tecnico = resolverTecnico(discordIdTecnico);
        if (tecnico == null) {
            return "🔒 Apenas técnicos e administradores de TI podem vincular usuários afetados.";
        }

        Ticket ticket = resolverTicket(ticketIdStr);
        if (ticket == null) {
            return "❌ Chamado não encontrado ou ID inválido: `" + ticketIdStr + "`";
        }

        // Limpa menção do Discord (<@123456789> → 123456789)
        String cleanDiscordId = discordIdAfetado
                .replace("<@", "")
                .replace("!", "")
                .replace(">", "")
                .trim();

        User afetado = userRepository.findByDiscordUserId(cleanDiscordId).orElse(null);
        if (afetado == null) {
            return "⚠️ Usuário <@" + cleanDiscordId + "> não está vinculado ao sistema Inovare TI. "
                    + "Peça para ele usar o comando `/vincular` primeiro.";
        }

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();

        // Verifica se já está vinculado
        if (ticket.getAdditionalUsers() != null
                && ticket.getAdditionalUsers().stream().anyMatch(u -> u.getId().equals(afetado.getId()))) {
            return "ℹ️ **" + afetado.getName() + "** já está vinculado como afetado no chamado #" + shortId + ".";
        }

        addAdditionalUserUseCase.execute(ticket.getId(), afetado.getId());

        log.info("[DISCORD] Usuário '{}' vinculado como afetado no chamado #{} por técnico '{}'",
                afetado.getName(), shortId, tecnico.getName());

        return "✅ **" + afetado.getName() + "** foi vinculado como afetado no chamado #" + shortId
                + ". Ele receberá uma notificação quando o chamado for resolvido.";
    }

    /**
     * Lista os chamados ativos (OPEN/IN_PROGRESS) do técnico autenticado.
     * Exibe ID, categoria, SLA restante e localização do solicitante.
     *
     * @param discordIdTecnico  Discord ID do técnico que executa o comando
     */
    @Transactional(readOnly = true)
    public String listarMeusAtendimentos(String discordIdTecnico) {
        User tecnico = resolverTecnico(discordIdTecnico);
        if (tecnico == null) {
            return "🔒 Este comando é restrito a técnicos e administradores de TI.";
        }

        List<Ticket> ativos = ticketRepository.findByRequesterIdAndStatusInOrderByCreatedAtDesc(
                tecnico.getId(),
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
        );

        // Também busca os chamados atribuídos ao técnico
        List<Ticket> atribuidos = ticketRepository.findAllByStatus(TicketStatus.OPEN)
                .stream()
                .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(tecnico.getId()))
                .toList();

        List<Ticket> atribuidosInProgress = ticketRepository.findAllByStatus(TicketStatus.IN_PROGRESS)
                .stream()
                .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(tecnico.getId()))
                .toList();

        java.util.Set<UUID> seen = new java.util.HashSet<>();
        java.util.List<Ticket> todos = new java.util.ArrayList<>();
        for (Ticket t : ativos) {
            if (seen.add(t.getId())) todos.add(t);
        }
        for (Ticket t : atribuidos) {
            if (seen.add(t.getId())) todos.add(t);
        }
        for (Ticket t : atribuidosInProgress) {
            if (seen.add(t.getId())) todos.add(t);
        }

        if (todos.isEmpty()) {
            return "📭 Você não possui chamados em andamento no momento!";
        }

        LocalDateTime now = LocalDateTime.now();
        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Seus atendimentos em andamento (").append(todos.size()).append("):**\n\n");

        for (Ticket t : todos) {
            String shortId = t.getId().toString().substring(0, 8).toUpperCase();
            String categoria = t.getCategory() != null ? t.getCategory().getName() : "N/A";
            String localizacao = t.getRequester() != null && t.getRequester().getLocation() != null
                    ? t.getRequester().getLocation()
                    : "N/A";

            String slaInfo;
            if (t.getSlaDeadline() != null) {
                Duration restante = Duration.between(now, t.getSlaDeadline());
                if (restante.isNegative()) {
                    slaInfo = "🔴 **EXPIRADO** há " + Math.abs(restante.toMinutes()) + "min";
                } else if (restante.toMinutes() < 30) {
                    slaInfo = "🚨 " + restante.toMinutes() + "min restantes";
                } else if (restante.toHours() < 2) {
                    slaInfo = "⚠️ " + restante.toMinutes() + "min restantes";
                } else {
                    slaInfo = "✅ " + restante.toHours() + "h restantes";
                }
            } else {
                slaInfo = "⚪ Sem SLA definido";
            }

            sb.append("▸ **#").append(shortId).append("** — ").append(t.getTitle()).append("\n")
              .append("  🏷️ Categoria: ").append(categoria).append("\n")
              .append("  ⏱️ SLA: ").append(slaInfo).append("\n")
              .append("  📍 Local: ").append(localizacao).append("\n\n");
        }

        return sb.toString().trim();
    }
}
