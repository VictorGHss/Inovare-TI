package br.dev.ctrls.inovareti.modules.inventory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para criação de uma categoria de item de inventário.
 */
public record ItemCategoryRequestDTO(

        @NotBlank(message = "O nome da categoria é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        Boolean isConsumable

) {}
