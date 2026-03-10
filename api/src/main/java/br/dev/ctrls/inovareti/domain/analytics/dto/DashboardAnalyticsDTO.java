package br.dev.ctrls.inovareti.domain.analytics.dto;

import java.util.List;

/**
 * DTO com os dados de analítica do dashboard.
 * Fornece métricas abrangentes para a visão principal do dashboard,
 * incluindo breakdown por status, métricas por categoria e resumo de estoque.
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
    List<MetricDTO> ticketsBySector,
    List<MetricDTO> ticketsByRequester,
    InventorySummaryDTO inventorySummary,
    long totalAssets,
    long assetsInUse,
    long assetsInStock
) {}
