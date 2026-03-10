package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * DTO com as métricas resumidas do estoque.
 */
public record InventorySummaryDTO(
    long totalItems,
    long lowStockItems,
    long outOfStockItems,
    long receivedItemsCount
) {}
