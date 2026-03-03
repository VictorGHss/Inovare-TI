package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO for opening a new ticket.
 * The initial status (OPEN) and slaDeadline are calculated automatically.
 * The requester is extracted from the JWT token in the SecurityContext.
 */
public record TicketRequestDTO(

        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must have at most 200 characters.")
        String title,

        String description,

        @Size(max = 50, message = "AnyDesk code must have at most 50 characters.")
        String anydeskCode,

        @NotNull(message = "Priority is required.")
        TicketPriority priority,

        /** ID of the assigned technician. Optional on creation. */
        UUID assignedToId,

        @NotNull(message = "Category ID is required.")
        UUID categoryId,

        /** ID of the requested item. Fill together with requestedQuantity. */
        UUID requestedItemId,

        @Positive(message = "Requested quantity must be greater than zero.")
        Integer requestedQuantity

) {}
