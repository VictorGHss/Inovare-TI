package br.dev.ctrls.inovareti.modules.asset.application.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;

public record AssetCategoryResponseDTO(
        UUID id,
        String name
) {
    public static AssetCategoryResponseDTO from(AssetCategory category) {
        return new AssetCategoryResponseDTO(category.getId(), category.getName());
    }
}
