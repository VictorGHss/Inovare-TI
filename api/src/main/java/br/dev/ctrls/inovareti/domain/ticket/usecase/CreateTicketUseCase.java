package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
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

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ItemRepository itemRepository;
    private final CreateNotificationService createNotificationService;
    private final DiscordWebhookService discordWebhookService;

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

        Ticket ticket = Ticket.builder()
                .title(request.title())
                .description(request.description())
                .anydeskCode(request.anydeskCode())
                .status(TicketStatus.OPEN)
                .priority(request.priority())
                .requester(requester)
                .assignedTo(assignedTo)
                .category(category)
                .requestedItem(requestedItem)
                .requestedQuantity(request.requestedQuantity())
                .slaDeadline(now.plusHours(category.getBaseSlaHours()))
                .createdAt(now)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Ticket created with ID: {} by user: {} ({}), category: {}, priority: {}",
                savedTicket.getId(), requester.getName(), requester.getEmail(),
                category.getName(), savedTicket.getPriority());

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
