package br.dev.ctrls.inovareti.domain.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO de entrada para registro de um lote de estoque.
 * O campo {@code itemId} é preenchido pelo path variable no controller;
 * não precisa constar no body da requisição.
 * Ao registrar um lote, o currentStock do item é incrementado automaticamente.
 */
public record StockBatchRequestDTO(

        UUID itemId,

        @NotNull(message = "A quantidade é obrigatória.")
        @Positive(message = "A quantidade deve ser maior que zero.")
        Integer quantity,

        @NotNull(message = "O preço unitário é obrigatório.")
        @DecimalMin(value = "0.01", message = "O preço unitário deve ser maior que zero.")
        BigDecimal unitPrice,

        String brand,

        String supplier,

        String purchaseReason

) {}
