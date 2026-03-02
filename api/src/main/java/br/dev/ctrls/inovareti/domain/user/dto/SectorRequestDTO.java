package br.dev.ctrls.inovareti.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para criação de um setor.
 */
public record SectorRequestDTO(

        @NotBlank(message = "O nome do setor é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name

) {}
