package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para abertura de um chamado.
 * O status inicial (OPEN) e o slaDeadline são calculados automaticamente.
 */
public record TicketRequestDTO(

        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 200, message = "O título deve ter no máximo 200 caracteres.")
        String title,

        String description,

        @Size(max = 50, message = "O código AnyDesk deve ter no máximo 50 caracteres.")
        String anydeskCode,

        @NotNull(message = "A prioridade é obrigatória.")
        TicketPriority priority,

        @NotNull(message = "O ID do solicitante é obrigatório.")
        UUID requesterId,

        /** ID do técnico responsável. Opcional na abertura. */
        UUID assignedToId,

        @NotNull(message = "O ID da categoria é obrigatório.")
        UUID categoryId,

        /** ID do item solicitado. Preencher junto com requestedQuantity. */
        UUID requestedItemId,

        @Positive(message = "A quantidade solicitada deve ser maior que zero.")
        Integer requestedQuantity

) {}
