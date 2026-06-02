package br.dev.ctrls.inovareti.modules.inventory.application.dto;

import java.util.Map;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;

/**
 * DTO de saída com os dados públicos de um item de inventário.
 */
public record ItemResponseDTO(
        UUID id,
        UUID itemCategoryId,
        String itemCategoryName,
        String name,
        Integer currentStock,
        Map<String, Object> specifications
) {
    /** Converte uma entidade {@link Item} para este DTO. */
    public static ItemResponseDTO from(Item item) {
        return new ItemResponseDTO(
                item.getId(),
                item.getItemCategory().getId(),
                item.getItemCategory().getName(),
                item.getName(),
                item.getCurrentStock(),
                item.getSpecifications()
        );
    }
}
