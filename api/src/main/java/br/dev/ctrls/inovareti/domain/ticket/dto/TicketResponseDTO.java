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
        boolean isFromDiscord,
        LocalDateTime slaDeadline,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        List<AttachmentResponseDTO> attachments,
        String solutionText,
        String solucao,
        List<UUID> relatedTicketIds,
        List<String> tags
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
                isFromDiscord(ticket),
                ticket.getSlaDeadline(),
                ticket.getCreatedAt(),
                ticket.getClosedAt(),
                List.of(), // Lista vazia por padrão, pode ser populada pelo caso de uso
                ticket.getSolutionText(),
                ticket.getSolutionText(),
                ticket.getRelatedTickets() != null ? ticket.getRelatedTickets().stream().map(Ticket::getId).toList() : List.of(),
                ticket.getTags() != null ? ticket.getTags().stream().toList() : List.of()
        );
    }

    private static boolean isFromDiscord(Ticket ticket) {
        String description = ticket.getDescription();
        return description != null && description.startsWith("[DISCORD]");
    }
}
