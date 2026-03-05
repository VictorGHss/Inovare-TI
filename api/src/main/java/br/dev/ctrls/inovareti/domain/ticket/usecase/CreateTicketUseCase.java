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
 * Use case: opens a new ticket.
 * Responsibilities:
 *   1. Resolve requester from JWT via SecurityContext.
 *   2. Validate category and optional assigned technician / requested item.
 *   3. Compute slaDeadline by adding baseSlaHours to the current time.
 *   4. Set initial status as OPEN.
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
     * Opens a ticket with the provided information.
     * The requester is resolved from the authenticated user in the SecurityContext.
     *
     * @param request DTO with the ticket data
     * @return DTO with the created ticket data
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

        // Send Discord webhook notification asynchronously
        discordWebhookService.sendNewTicketAlert(savedTicket);

        // Notify all ADMIN and TECHNICIAN users about the new ticket
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
