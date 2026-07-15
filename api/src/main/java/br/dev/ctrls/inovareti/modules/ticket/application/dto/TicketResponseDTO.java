package br.dev.ctrls.inovareti.modules.ticket.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;

/**
 * DTO de saída com os dados públicos de um chamado.
 */
public record TicketResponseDTO(
        UUID id,
        String title,
        String description,
        String anydeskCode,
        TicketStatus status,
        TicketPriority priority,
        UUID requesterId,
        String requesterName,
        String requesterLocation,
        UUID assignedToId,
        String assignedToName,
        UUID categoryId,
        String categoryName,
        UUID requestedItemId,
        String requestedItemName,
        Integer requestedQuantity,
        boolean isFromDiscord,
        LocalDateTime slaDeadline,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        List<AttachmentResponseDTO> attachments,
        String solutionText,
        String solucao,
        List<UUID> relatedTicketIds,
        List<TicketTag> tags,
        List<UUID> additionalUserIds,
        List<UUID> assignedUserIds,
        UUID assetId,
        String assetName,
        boolean isAssetCritical,
        List<TicketItemRequestResponseDTO> requestedItems
) {
    /** Converte uma entidade {@link Ticket} para este DTO. */
    public static TicketResponseDTO from(Ticket ticket) {
        return from(ticket, List.of());
    }

    public static TicketResponseDTO from(Ticket ticket, List<AttachmentResponseDTO> attachments) {
        return new TicketResponseDTO(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getAnydeskCode(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getRequester().getId(),
                ticket.getRequester().getName(),
                ticket.getRequester().getLocation(),
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : null,
                ticket.getCategory().getId(),
                ticket.getCategory().getName(),
                ticket.getRequestedItem() != null ? ticket.getRequestedItem().getId() : null,
                ticket.getRequestedItem() != null ? ticket.getRequestedItem().getName() : null,
                ticket.getRequestedQuantity(),
                isFromDiscord(ticket),
                ticket.getSlaDeadline(),
                ticket.getCreatedAt(),
                ticket.getClosedAt(),
                attachments,
                ticket.getSolutionText(),
                ticket.getSolutionText(),
                ticket.getRelatedTickets() != null ? ticket.getRelatedTickets().stream().map(t -> t.getId()).toList() : List.of(),
                ticket.getTags() != null ? ticket.getTags().stream().toList() : List.of(),
                ticket.getAdditionalUsers() != null ? ticket.getAdditionalUsers().stream().map(u -> u.getId()).toList() : List.of(),
                ticket.getAssignedUsers() != null ? ticket.getAssignedUsers().stream().map(u -> u.getId()).toList() : List.of(),
                ticket.getAsset() != null ? ticket.getAsset().getId() : null,
                ticket.getAsset() != null ? ticket.getAsset().getName() : null,
                ticket.getAsset() != null && ticket.getAsset().isCritical(),
                ticket.getRequestedItems() != null ? ticket.getRequestedItems().stream()
                        .map(ri -> new TicketItemRequestResponseDTO(
                                ri.getItem().getId(),
                                ri.getItem().getName(),
                                ri.getQuantity()
                        )).toList() : List.of()
        );
    }

    private static boolean isFromDiscord(Ticket ticket) {
        String description = ticket.getDescription();
        return description != null && description.startsWith("[DISCORD]");
    }

    public record TicketItemRequestResponseDTO(
            UUID itemId,
            String itemName,
            Integer quantity
    ) {}
}
