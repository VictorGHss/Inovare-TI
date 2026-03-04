package br.dev.ctrls.inovareti.domain.analytics.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.analytics.dto.DashboardAnalyticsDTO;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
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

    private static final int LOW_STOCK_THRESHOLD = 3;

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
        long closedTickets;
        long lowStockItems;

        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN and TECHNICIAN see all tickets
            openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByStatus(TicketStatus.RESOLVED);
            closedTickets = ticketRepository.countByStatus(TicketStatus.CLOSED);
        } else {
            // USER sees only their own tickets
            openTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.RESOLVED);
            closedTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.CLOSED);
        }

        lowStockItems = itemRepository.countByCurrentStockLessThanEqual(LOW_STOCK_THRESHOLD);

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
                closedTickets
        );
    }
}
