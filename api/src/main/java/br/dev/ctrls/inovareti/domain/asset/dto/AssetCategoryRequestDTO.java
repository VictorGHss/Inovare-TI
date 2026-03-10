package br.dev.ctrls.inovareti.domain.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssetCategoryRequestDTO(
        @NotBlank(message = "O nome da categoria é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name
) {}
