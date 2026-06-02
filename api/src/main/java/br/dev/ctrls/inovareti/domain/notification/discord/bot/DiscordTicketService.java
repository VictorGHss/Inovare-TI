package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketCategory;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordTicketService {

    private final UserRepository userRepository;
    private final TicketRepositoryPort ticketRepository;
    private final TicketCategoryRepositoryPort ticketCategoryRepository;
    private final DiscordWebhookService discordWebhookService;
    private final br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort assetRepository;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketTagRepositoryPort ticketTagRepository;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTagExtractor ticketTagExtractor;

    @Transactional
    public String createTicketFromDiscord(String discordUserId, String description, String priorityRaw, String patrimonioOption) {
        User requester = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (requester == null) {
            return "⚠️ Seu Discord não está vinculado à sua conta da clínica. Use o comando /vincular [seu-email].";
        }

        TicketCategory category = ticketCategoryRepository.findAll().stream()
                .findFirst()
            .orElseThrow(() -> new IllegalStateException("Nenhuma categoria de chamado foi encontrada no banco de dados"));

        String normalizedDescription = description.trim();
        String title = normalizedDescription.length() > 40
            ? normalizedDescription.substring(0, 37) + "..."
            : normalizedDescription;
        String storedDescription = "[DISCORD] " + normalizedDescription;

        // 1. Resolve o ativo (Asset) por ID/código ou por varredura de Regex no texto
        br.dev.ctrls.inovareti.modules.asset.domain.model.Asset asset = null;
        if (patrimonioOption != null && !patrimonioOption.isBlank()) {
            asset = assetRepository.findByPatrimonyCode(patrimonioOption.trim().toUpperCase()).orElse(null);
        } else {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("INV-\\d{4}-\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(description);
            if (matcher.find()) {
                String code = matcher.group();
                asset = assetRepository.findByPatrimonyCode(code.trim().toUpperCase()).orElse(null);
            }
        }

        // 2. Extrai as tags automáticas
        java.util.Set<br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag> extractedTags = ticketTagExtractor.extractTags(title, storedDescription);

        // 3. Aplica prioridade e SLA de acordo com a criticidade
        TicketPriority priority = parsePriority(priorityRaw);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime finalSlaDeadline = now.plusHours(category.getBaseSlaHours());

        if (asset != null && asset.isCritical()) {
            priority = TicketPriority.URGENT;
            finalSlaDeadline = now.plusHours(1); // 1h SLA agressivo

            // Injeta tag #🚨ParadaCrítica
            br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag criticalTag = ticketTagRepository.findByNameIgnoreCase("#🚨ParadaCrítica").orElseGet(() -> {
                br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag newTag = br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag.builder()
                        .name("#🚨ParadaCrítica")
                        .color("#EF4444")
                        .active(true)
                        .defaultResolution("Equipamento crítico paralisado. Substituição emergencial ou manutenção prioritária realizada.")
                        .build();
                return ticketTagRepository.save(newTag);
            });
            extractedTags.add(criticalTag);
        }

        Ticket ticket = Ticket.builder()
            .title(title)
            .description(storedDescription)
            .status(TicketStatus.OPEN)
            .priority(priority)
            .requester(requester)
            .category(category)
            .slaDeadline(finalSlaDeadline)
            .createdAt(now)
            .tags(extractedTags)
            .asset(asset)
            .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        initializeWebhookRelations(savedTicket);
        discordWebhookService.sendNewTicketAlert(savedTicket);

        String ticketIdShort = savedTicket.getId().toString().substring(0, 8).toUpperCase();

        return "✅ Chamado #" + ticketIdShort + " aberto com sucesso! A TI foi notificada.";
    }

    @Transactional(readOnly = true)
    public String getTicketStatusFromDiscord(String rawTicketId) {
        Ticket ticket = findTicketByIdentifier(rawTicketId);
        if (ticket == null) {
            return "❌ Chamado não encontrado.";
        }

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        return String.format(
                "🔍 O chamado #%s está atualmente: **%s**",
                shortId,
                toFriendlyStatus(ticket.getStatus())
        );
    }

    @Transactional(readOnly = true)
    public String listMyActiveTicketsFromDiscord(String discordUserId) {
        User requester = userRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (requester == null) {
            return "⚠️ Sua conta não está vinculada! Digite o comando /vincular e informe seu e-mail corporativo. Depois, é só usar o /meuschamados novamente!";
        }

        List<TicketStatus> activeStatuses = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        List<Ticket> activeTickets = ticketRepository.findByRequesterIdAndStatusInOrderByCreatedAtDesc(
                requester.getId(),
                activeStatuses
        );

        if (activeTickets.isEmpty()) {
            return "Você não tem nenhum chamado em andamento no momento! 🎉";
        }

        StringBuilder messageBuilder = new StringBuilder("Seus chamados em andamento:\n");
        for (Ticket ticket : activeTickets) {
            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            messageBuilder
                    .append("📌 #")
                    .append(shortId)
                    .append(" - ")
                    .append(ticket.getTitle())
                    .append(" (Status: ")
                    .append(toFriendlyStatus(ticket.getStatus()))
                    .append(")\n");
        }

        return messageBuilder.toString().trim();
    }

    private TicketPriority parsePriority(String priorityRaw) {
        if (priorityRaw == null || priorityRaw.isBlank()) {
            return TicketPriority.NORMAL;
        }
        try {
            return TicketPriority.valueOf(priorityRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Prioridade '{}' inválida. Aplicando NORMAL como padrão.", priorityRaw);
            return TicketPriority.NORMAL;
        }
    }

    private Ticket findTicketByIdentifier(String rawTicketId) {
        if (rawTicketId == null || rawTicketId.isBlank()) {
            return null;
        }

        if (rawTicketId.length() < 36) {
            return ticketRepository.findByShortIdStartingWith(rawTicketId)
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        try {
            UUID ticketId = UUID.fromString(rawTicketId);
            return ticketRepository.findById(ticketId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String toFriendlyStatus(TicketStatus status) {
        return switch (status) {
            case OPEN -> "ABERTO";
            case IN_PROGRESS -> "EM ANDAMENTO";
            case RESOLVED -> "RESOLVIDO";
        };
    }

    private void initializeWebhookRelations(Ticket ticket) {
        ticket.getRequester().getName();
        ticket.getRequester().getSector().getName();
        ticket.getCategory().getName();
    }
}
