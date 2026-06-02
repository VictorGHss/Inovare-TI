package br.dev.ctrls.inovareti.modules.inventory.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType;

public record StockMovementResponseDTO(
        UUID id,
        UUID itemId,
        StockMovementType type,
        Integer quantity,
        String reference,
        LocalDateTime date
) {
    public static StockMovementResponseDTO from(StockMovement movement) {
        return new StockMovementResponseDTO(
                movement.getId(),
                movement.getItemId(),
                movement.getType(),
                movement.getQuantity(),
                movement.getReference(),
                movement.getDate()
        );
    }
}
