package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;

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
        UUID assignedToId,
        String assignedToName,
        UUID categoryId,
        String categoryName,
        UUID requestedItemId,
        String requestedItemName,
        Integer requestedQuantity,
        LocalDateTime slaDeadline,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        List<AttachmentResponseDTO> attachments
) {
    /** Converte uma entidade {@link Ticket} para este DTO. */
    public static TicketResponseDTO from(Ticket ticket) {
        return new TicketResponseDTO(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getAnydeskCode(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getRequester().getId(),
                ticket.getRequester().getName(),
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : null,
                ticket.getCategory().getId(),
                ticket.getCategory().getName(),
                ticket.getRequestedItem() != null ? ticket.getRequestedItem().getId() : null,
                ticket.getRequestedItem() != null ? ticket.getRequestedItem().getName() : null,
                ticket.getRequestedQuantity(),
                ticket.getSlaDeadline(),
                ticket.getCreatedAt(),
                ticket.getClosedAt(),
                List.of() // Empty list by default, can be populated by use case
        );
    }
}
