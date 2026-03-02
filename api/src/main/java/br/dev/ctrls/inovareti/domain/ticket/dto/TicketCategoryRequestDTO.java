package br.dev.ctrls.inovareti.domain.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para criação de uma categoria de chamado.
 */
public record TicketCategoryRequestDTO(

        @NotBlank(message = "O nome da categoria é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "O SLA base em horas é obrigatório.")
        @Min(value = 1, message = "O SLA base deve ser de no mínimo 1 hora.")
        Integer baseSlaHours

) {}
