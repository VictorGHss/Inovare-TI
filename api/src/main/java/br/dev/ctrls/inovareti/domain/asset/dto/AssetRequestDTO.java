package br.dev.ctrls.inovareti.domain.asset.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetRequestDTO(

        @NotNull(message = "User id is required.")
        UUID userId,

        @NotBlank(message = "Asset name is required.")
        @Size(max = 150, message = "Asset name must have at most 150 characters.")
        String name,

        @NotBlank(message = "Patrimony code is required.")
        @Size(max = 80, message = "Patrimony code must have at most 80 characters.")
        String patrimonyCode,

        UUID categoryId,

        String specifications

) {}
