package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * DTO representing inventory summary metrics.
 */
public record InventorySummaryDTO(
    long totalItems,
    long lowStockItems,
    long outOfStockItems,
    long receivedItemsCount
) {}
