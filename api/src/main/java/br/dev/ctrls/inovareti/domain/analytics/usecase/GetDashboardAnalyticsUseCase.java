package br.dev.ctrls.inovareti.domain.analytics.usecase;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.analytics.dto.DashboardAnalyticsDTO;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case: retrieves dashboard analytics data.
 * Provides key metrics for the main dashboard view including ticket counts by status
 * and low stock alerts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetDashboardAnalyticsUseCase {

    private final TicketRepository ticketRepository;
    private final ItemRepository itemRepository;

    private static final int LOW_STOCK_THRESHOLD = 3;

    /**
     * Gathers analytics data for the dashboard.
     * @return DTO with aggregated metrics
     */
    @Transactional(readOnly = true)
    public DashboardAnalyticsDTO execute() {
        log.info("Fetching dashboard analytics data");

        long openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgressTickets = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long resolvedTickets = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        long lowStockItems = itemRepository.countByCurrentStockLessThanEqual(LOW_STOCK_THRESHOLD);

        log.info("Analytics retrieved: open={}, inProgress={}, resolved={}, lowStock={}",
                openTickets, inProgressTickets, resolvedTickets, lowStockItems);

        return new DashboardAnalyticsDTO(
                openTickets,
                inProgressTickets,
                resolvedTickets,
                lowStockItems
        );
    }
}
