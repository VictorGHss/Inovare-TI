package br.dev.ctrls.inovareti.domain.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record StockBatchInstallmentRequestDTO(
        @NotNull(message = "A data de vencimento da parcela é obrigatória.")
        LocalDate dueDate,

        @NotNull(message = "O valor da parcela é obrigatório.")
        @DecimalMin(value = "0.01", message = "O valor da parcela deve ser maior que zero.")
        BigDecimal amount
) {}
