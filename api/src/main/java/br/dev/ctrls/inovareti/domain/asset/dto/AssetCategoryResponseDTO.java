package br.dev.ctrls.inovareti.domain.asset.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.asset.AssetCategory;

public record AssetCategoryResponseDTO(
        UUID id,
        String name
) {
    public static AssetCategoryResponseDTO from(AssetCategory category) {
        return new AssetCategoryResponseDTO(category.getId(), category.getName());
    }
}
