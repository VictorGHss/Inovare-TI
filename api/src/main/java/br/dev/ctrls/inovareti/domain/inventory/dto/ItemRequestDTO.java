package br.dev.ctrls.inovareti.domain.inventory.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para criação de um item de inventário.
 * O estoque inicial é sempre zero; use os lotes para registrar entradas.
 */
public record ItemRequestDTO(

        @NotNull(message = "A categoria do item é obrigatória.")
        UUID itemCategoryId,

        @NotBlank(message = "O nome do item é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        /**
         * Especificações técnicas opcionais em formato chave-valor.
         * Exemplo: {"marca": "Brother", "modelo": "HL-L2360DW"}
         */
        Map<String, Object> specifications

) {}
