package br.dev.ctrls.inovareti.domain.analytics.usecase;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.analytics.dto.DashboardAnalyticsDTO;
import br.dev.ctrls.inovareti.domain.analytics.dto.InventorySummaryDTO;
import br.dev.ctrls.inovareti.domain.analytics.dto.MetricDTO;
import br.dev.ctrls.inovareti.domain.asset.AssetRepository;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case: retrieves dashboard analytics data with tenant isolation.
 * 
 * - ADMIN/TECHNICIAN: see all ticket counts
 * - USER: see only their own ticket counts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetDashboardAnalyticsUseCase {

    private final TicketRepository ticketRepository;
    private final ItemRepository itemRepository;
        private final AssetRepository assetRepository;

    private static final int LOW_STOCK_THRESHOLD = 3;
    private static final int OUT_OF_STOCK_THRESHOLD = 0;

    /**
     * Gathers analytics data for the dashboard.
     * 
     * @param userId the authenticated user's ID
     * @param userRole the authenticated user's role
     * @return DTO with aggregated metrics
     */
    @Transactional(readOnly = true)
    public DashboardAnalyticsDTO execute(UUID userId, UserRole userRole) {
        log.info("Fetching dashboard analytics data for user: {} (role: {})", userId, userRole);

        long openTickets;
        long inProgressTickets;
        long resolvedTickets;
        List<Ticket> allTickets;

        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN and TECHNICIAN see all tickets
            openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByStatus(TicketStatus.RESOLVED);
            allTickets = ticketRepository.findAll();
        } else {
            // USER sees only their own tickets
            openTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.RESOLVED);
            allTickets = ticketRepository.findByRequesterId(userId);
        }

        long closedTickets = 0;

        // Build tickets by status metrics
        List<MetricDTO> ticketsByStatus = buildTicketsByStatusMetrics(openTickets, inProgressTickets, resolvedTickets);

        // Build tickets by category metrics
        List<MetricDTO> ticketsByCategory = buildTicketsByCategoryMetrics(allTickets);

        // Build tickets by sector metrics (Top 5)
        List<MetricDTO> ticketsBySector = buildTicketsBySectorMetrics(allTickets);

        // Build tickets by requester metrics (Top 5)
        List<MetricDTO> ticketsByRequester = buildTicketsByRequesterMetrics(allTickets);

        // Build inventory summary metrics
        long totalItems = itemRepository.count();
        long lowStockItems = itemRepository.countByCurrentStockLessThanEqual(LOW_STOCK_THRESHOLD);
        long outOfStockItems = itemRepository.countByCurrentStockLessThanEqual(OUT_OF_STOCK_THRESHOLD);
        long receivedItemsCount = 0;

        if (userRole == UserRole.USER) {
            long receivedInventoryItems = allTickets.stream()
                    .filter(ticket -> ticket.getStatus() == TicketStatus.RESOLVED)
                    .filter(ticket -> ticket.getRequestedItem() != null)
                    .mapToLong(ticket -> ticket.getRequestedQuantity() != null
                            ? ticket.getRequestedQuantity().longValue()
                            : 0L)
                    .sum();

            long receivedAssets = assetRepository.countByUserId(userId);
            receivedItemsCount = receivedInventoryItems + receivedAssets;
        }

        InventorySummaryDTO inventorySummary = new InventorySummaryDTO(
                totalItems,
                lowStockItems,
                outOfStockItems,
                receivedItemsCount
        );

        log.info("Analytics retrieved: open={}, inProgress={}, resolved={}, closed={}, lowStock={}",
                openTickets, inProgressTickets, resolvedTickets, closedTickets, lowStockItems);

        // Total tickets = sum of all statuses
        long totalTickets = openTickets + inProgressTickets + resolvedTickets + closedTickets;

        return new DashboardAnalyticsDTO(
                openTickets,
                inProgressTickets,
                resolvedTickets,
                lowStockItems,
                totalTickets,
                closedTickets,
                ticketsByStatus,
                ticketsByCategory,
                ticketsBySector,
                ticketsByRequester,
                inventorySummary
        );
    }

    /**
     * Builds a list of metrics representing tickets by status.
     */
    private List<MetricDTO> buildTicketsByStatusMetrics(long open, long inProgress, long resolved) {
        return List.of(
                new MetricDTO("Aberto", open),
                new MetricDTO("Em Progresso", inProgress),
                new MetricDTO("Resolvido", resolved)
        );
    }

    /**
     * Builds a list of metrics representing tickets by category.
     */
    private List<MetricDTO> buildTicketsByCategoryMetrics(List<Ticket> tickets) {
        return tickets.stream()
                .collect(Collectors.groupingByConcurrent(
                        ticket -> ticket.getCategory().getName(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new MetricDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Builds a list of top 5 metrics representing tickets by sector.
     */
    private List<MetricDTO> buildTicketsBySectorMetrics(List<Ticket> tickets) {
        return tickets.stream()
                .collect(Collectors.groupingByConcurrent(
                        ticket -> ticket.getRequester().getSector().getName(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> new MetricDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Builds a list of top 5 metrics representing tickets by requester (user).
     */
    private List<MetricDTO> buildTicketsByRequesterMetrics(List<Ticket> tickets) {
        return tickets.stream()
                .collect(Collectors.groupingByConcurrent(
                        ticket -> ticket.getRequester().getName(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> new MetricDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
