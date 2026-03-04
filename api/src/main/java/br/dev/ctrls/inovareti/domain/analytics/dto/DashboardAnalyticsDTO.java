package br.dev.ctrls.inovareti.domain.analytics.dto;

import java.util.List;

/**
 * DTO for dashboard analytics data.
 * Provides comprehensive metrics for the main dashboard view,
 * including status breakdown, category metrics, and inventory summary.
 */
public record DashboardAnalyticsDTO(
    long totalOpenTickets,
    long totalInProgressTickets,
    long totalResolvedTickets,
    long lowStockItemsCount,
    long totalTickets,
    long totalClosedTickets,
    List<MetricDTO> ticketsByStatus,
    List<MetricDTO> ticketsByCategory,
    InventorySummaryDTO inventorySummary
) {}
