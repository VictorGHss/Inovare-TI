package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * DTO for dashboard analytics data.
 * Provides key metrics for the main dashboard view.
 */
public record DashboardAnalyticsDTO(
    long totalOpenTickets,
    long totalInProgressTickets,
    long totalResolvedTickets,
    long lowStockItemsCount
) {}
