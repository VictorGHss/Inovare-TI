package br.dev.ctrls.inovareti.modules.inventory.application.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO de entrada para registrar a alocação de um consumível/periférico com baixa do estoque.
 */
public record AllocateConsumableRequestDTO(
    @NotNull(message = "O identificador do item filho/consumível é obrigatório.")
    UUID childItemId,

    @NotNull(message = "A quantidade a ser alocada é obrigatória.")
    @Positive(message = "A quantidade alocada deve ser maior que zero.")
    Integer quantity,

    UUID ticketId
) {}
