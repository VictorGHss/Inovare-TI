package br.dev.ctrls.inovareti.modules.ticket.application.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO utilizado para atualizar a lista de itens de inventário associados a um chamado existente (atendimento ou incidente).
 */
public record UpdateTicketItemsDTO(
        
        @NotNull(message = "A lista de itens não pode ser nula.")
        @Valid
        List<RequestedItemUpdateDTO> items
) {

    /**
     * DTO que representa o vínculo individual de um item e a quantidade solicitada/associada.
     */
    public record RequestedItemUpdateDTO(
            
            @NotNull(message = "O ID do item de inventário é obrigatório.")
            UUID itemId,

            @NotNull(message = "A quantidade do item é obrigatória.")
            @Positive(message = "A quantidade deve ser maior que zero.")
            Integer quantity
    ) {}
}
