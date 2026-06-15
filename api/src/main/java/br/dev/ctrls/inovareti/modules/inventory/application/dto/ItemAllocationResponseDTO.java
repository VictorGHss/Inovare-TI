package br.dev.ctrls.inovareti.modules.inventory.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemAllocationEntity;

/**
 * DTO de resposta para expor os dados consolidados de uma alocação de inventário.
 */
public record ItemAllocationResponseDTO(
    UUID id,
    UUID parentItemId,
    String parentItemName,
    UUID childItemId,
    String childItemName,
    Integer quantity,
    LocalDateTime allocatedAt,
    UUID allocatedById,
    String allocatedByName,
    UUID ticketId
) {
    /**
     * Mapeia a entidade JPA {@link ItemAllocationEntity} para este DTO.
     */
    public static ItemAllocationResponseDTO from(ItemAllocationEntity entity) {
        return new ItemAllocationResponseDTO(
            entity.getId(),
            entity.getParentItem().getId(),
            entity.getParentItem().getName(),
            entity.getChildItem().getId(),
            entity.getChildItem().getName(),
            entity.getQuantity(),
            entity.getAllocatedAt(),
            entity.getAllocatedBy().getId(),
            entity.getAllocatedBy().getName(),
            entity.getTicket() != null ? entity.getTicket().getId() : null
        );
    }
}
