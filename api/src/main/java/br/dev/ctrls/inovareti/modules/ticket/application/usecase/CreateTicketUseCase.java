package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditAction;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditEvent;
import br.dev.ctrls.inovareti.modules.audit.application.service.AuditLogService;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketInventoryGateway;
import br.dev.ctrls.inovareti.modules.notification.application.service.CreateNotificationService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.DiscordWebhookService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketCategory;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTagExtractor;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketTagRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.event.TicketCreatedEvent;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: abre um novo chamado.
 * Responsabilidades:
 *   1. Resolver o solicitante via JWT pelo SecurityContext.
 *   2. Validar a categoria e o técnico/item solicitado (opcionais).
 *   3. Calcular o slaDeadline adicionando baseSlaHours ao momento atual.
 *   4. Definir o status inicial como OPEN.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateTicketUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;
    private final TicketCategoryRepositoryPort ticketCategoryRepository;
    private final TicketInventoryGateway itemRepository;
    private final CreateNotificationService createNotificationService;
    private final DiscordWebhookService discordWebhookService;
    private final AuditLogService auditLogService;
    private final TicketTagExtractor ticketTagExtractor;
    private final AssetRepositoryPort assetRepository;
    private final TicketTagRepositoryPort ticketTagRepository;
    private final DiscordDirectMessageService discordDirectMessageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Abre um chamado com os dados fornecidos.
     * O solicitante é resolvido pelo usuário autenticado no SecurityContext.
     *
     * @param request DTO com os dados do chamado
     * @return DTO com os dados do chamado criado
     */
    @Transactional
    public TicketResponseDTO execute(TicketRequestDTO request) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Authenticated user not found with id: " + userId));

        User assignedTo = null;
        if (request.assignedToId() != null) {
            assignedTo = userRepository.findById(request.assignedToId())
                    .orElseThrow(() -> new NotFoundException(
                            "Technician not found with id: " + request.assignedToId()));
        }

        TicketCategory category = ticketCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException(
                        "Category not found with id: " + request.categoryId()));

        Item requestedItem = null;
        if (request.requestedItemId() != null) {
            requestedItem = itemRepository.findById(request.requestedItemId())
                    .orElseThrow(() -> new NotFoundException(
                            "Requested item not found with id: " + request.requestedItemId()));
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Resolve o ativo (Asset) por ID ou por varredura de Regex no título/descrição
        Asset asset = null;
        if (request.assetId() != null) {
            asset = assetRepository.findById(request.assetId()).orElse(null);
        } else {
            String combinedText = request.title() + " " + (request.description() != null ? request.description() : "");
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("INV-\\d{4}-\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(combinedText);
            if (matcher.find()) {
                String code = matcher.group();
                asset = assetRepository.findByPatrimonyCode(code.trim().toUpperCase()).orElse(null);
            }
        }

        // 2. Extrai as tags automáticas a partir do texto
        java.util.Set<TicketTag> extractedTags = ticketTagExtractor.extractTags(request.title(), request.description());

        // 3. Aplica regras de criticidade se o ativo associado for crítico
        TicketPriority finalPriority = request.priority();
        LocalDateTime finalSlaDeadline = now.plusHours(category.getBaseSlaHours());

        if (asset != null && asset.isCritical()) {
            finalPriority = TicketPriority.URGENT;
            finalSlaDeadline = now.plusHours(1); // Exigência: 1 hora de SLA agressivo

            // Injeta tag #🚨ParadaCrítica
            TicketTag criticalTag = ticketTagRepository.findByNameIgnoreCase("#🚨ParadaCrítica").orElseGet(() -> {
                TicketTag newTag = TicketTag.builder()
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
                .title(request.title())
                .description(request.description())
                .anydeskCode(request.anydeskCode())
                .status(TicketStatus.OPEN)
                .priority(finalPriority)
                .requester(requester)
                .assignedTo(assignedTo)
                .category(category)
                .requestedItem(requestedItem)
                .requestedQuantity(request.requestedQuantity())
                .slaDeadline(finalSlaDeadline)
                .createdAt(now)
                .tags(extractedTags)
                .asset(asset)
                .build();

        java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketItemRequestEntity> reqItems = new java.util.ArrayList<>();
        if (request.requestedItems() != null && !request.requestedItems().isEmpty()) {
            for (var dtoItem : request.requestedItems()) {
                Item item = itemRepository.findById(dtoItem.itemId())
                        .orElseThrow(() -> new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Requested item not found with id: " + dtoItem.itemId()));
                reqItems.add(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketItemRequestEntity.builder()
                        .ticket(ticket)
                        .item(item)
                        .quantity(dtoItem.quantity())
                        .build());
            }
        } else if (requestedItem != null && request.requestedQuantity() != null) {
            reqItems.add(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketItemRequestEntity.builder()
                    .ticket(ticket)
                    .item(requestedItem)
                    .quantity(request.requestedQuantity())
                    .build());
        }
        ticket.setRequestedItems(reqItems);

        Ticket savedTicket = ticketRepository.save(ticket);
        
        java.util.List<User> assignedUsers = new java.util.ArrayList<>();
        if (assignedTo != null) {
            assignedUsers.add(assignedTo);
        }
        eventPublisher.publishEvent(new TicketCreatedEvent(savedTicket, assignedUsers));

        auditLogService.publish(AuditEvent.of(AuditAction.TICKET_OPEN)
            .userId(requester.getId())
            .resourceType("Ticket")
            .resourceId(savedTicket.getId())
            .details("{\"title\": \"" + savedTicket.getTitle() + "\"}")
            .build());
        log.info("Ticket created with ID: {} by user: {} ({}), category: {}, priority: {}",
                savedTicket.getId(), requester.getName(), requester.getEmail(),
                category.getName(), savedTicket.getPriority());

        // Envia alerta de DM se o ativo for crítico e houver técnico atribuído
        if (asset != null && asset.isCritical() && assignedTo != null) {
            String shortId = savedTicket.getId().toString().substring(0, 8).toUpperCase();
            String discordId = assignedTo.getDiscordUserId();
            if (discordId != null && !discordId.isBlank()) {
                String alertTitle = "🚨 ALERTA VERMELHO: EQUIPAMENTO CRÍTICO PARADO — Chamado #" + shortId;
                String alertMsg = """
                        **Urgente!** O chamado sob sua responsabilidade envolve um ativo crítico da operação:

                        🖥️ **Ativo:** %s (%s)
                        📋 **Título:** %s
                        ⏱️ **SLA reduzido:** 1 hora (Expira às %s)

                        ⚡ Dirija-se imediatamente para o atendimento físico ou remoto deste ativo!
                        """.formatted(
                        asset.getName(),
                        asset.getPatrimonyCode(),
                        savedTicket.getTitle(),
                        finalSlaDeadline.toLocalTime().toString().substring(0, 5)
                );
                discordDirectMessageService.sendTicketUpdateDMToUser(discordId, savedTicket.getId(), alertTitle, alertMsg);
            }
        }

        // Envia notificação de webhook do Discord de forma assíncrona
        discordWebhookService.sendNewTicketAlert(savedTicket);

        // Notifica todos os usuários ADMIN e TECHNICIAN sobre o novo chamado
        var adminUsers = userRepository.findAllByRole(UserRole.ADMIN);
        var technicianUsers = userRepository.findAllByRole(UserRole.TECHNICIAN);

        String ticketIdShort = savedTicket.getId().toString().substring(0, 8).toUpperCase();
        String notificationTitle = "Novo chamado aberto";
        String notificationMessage = String.format("Novo chamado #%s: %s", ticketIdShort, savedTicket.getTitle());
        String notificationLink = "/tickets/" + savedTicket.getId();

        for (User admin : adminUsers) {
            createNotificationService.create(
                admin.getId(),
                notificationTitle,
                notificationMessage,
                notificationLink
            );
        }

        for (User technician : technicianUsers) {
            createNotificationService.create(
                technician.getId(),
                notificationTitle,
                notificationMessage,
                notificationLink
            );
        }

        return TicketResponseDTO.from(savedTicket);
    }
}
