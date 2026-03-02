package br.dev.ctrls.inovareti.domain.inventory.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.inventory.ItemCategory;

/**
 * DTO de saída com os dados de uma categoria de item de inventário.
 */
public record ItemCategoryResponseDTO(
        UUID id,
        String name,
        Boolean isConsumable
) {
    /** Converte uma entidade {@link ItemCategory} para este DTO. */
    public static ItemCategoryResponseDTO from(ItemCategory category) {
        return new ItemCategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getIsConsumable()
        );
    }
}
