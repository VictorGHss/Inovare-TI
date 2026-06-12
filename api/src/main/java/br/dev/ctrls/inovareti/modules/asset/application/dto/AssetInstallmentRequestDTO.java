package br.dev.ctrls.inovareti.modules.asset.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para o registo de uma parcela de financiamento de um equipamento (ativo imobilizado).
 */
public record AssetInstallmentRequestDTO(
        @NotNull(message = "A data de vencimento da parcela é obrigatória.")
        LocalDate dueDate,

        @NotNull(message = "O valor da parcela é obrigatório.")
        @DecimalMin(value = "0.01", message = "O valor da parcela deve ser maior que zero.")
        BigDecimal amount
) {}
