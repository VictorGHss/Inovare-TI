package br.dev.ctrls.inovareti.modules.inventory.application.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para registrar o vínculo (acoplamento) de um ativo componente a um principal.
 */
public record LinkComponentRequestDTO(
    @NotNull(message = "O identificador do componente filho é obrigatório.")
    UUID componentId
) {}
