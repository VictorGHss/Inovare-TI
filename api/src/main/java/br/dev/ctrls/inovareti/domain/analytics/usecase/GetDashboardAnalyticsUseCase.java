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
 * Caso de uso: recupera os dados de analítica do dashboard com isolamento por perfil.
 *
 * - ADMIN/TECHNICIAN: visualiza todos os chamados
 * - USER: visualiza apenas seus próprios chamados
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
            // ADMIN e TECHNICIAN visualizam todos os chamados
            openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByStatus(TicketStatus.RESOLVED);
            allTickets = ticketRepository.findAll();
        } else {
            // USER visualiza apenas seus próprios chamados
            openTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.RESOLVED);
            allTickets = ticketRepository.findByRequesterId(userId);
        }

        long closedTickets = 0;

        // Agrega métricas de chamados por status
        List<MetricDTO> ticketsByStatus = buildTicketsByStatusMetrics(openTickets, inProgressTickets, resolvedTickets);

        // Agrega métricas de chamados por categoria
        List<MetricDTO> ticketsByCategory = buildTicketsByCategoryMetrics(allTickets);

        // Agrega métricas de chamados por setor (Top 5)
        List<MetricDTO> ticketsBySector = buildTicketsBySectorMetrics(allTickets);

        // Agrega métricas de chamados por solicitante (Top 5)
        List<MetricDTO> ticketsByRequester = buildTicketsByRequesterMetrics(allTickets);

        // Agrega métricas de estoque
        // SEGURANÇA: apenas ADMIN e TECHNICIAN visualizam dados globais de estoque
        long totalItems;
        long lowStockItems;
        long outOfStockItems;
        long receivedItemsCount = 0;

        if (userRole == UserRole.USER) {
            // USER visualiza apenas a contagem de itens recebidos; sem dados globais de estoque
            totalItems = 0;
            lowStockItems = 0;
            outOfStockItems = 0;

            long receivedInventoryItems = allTickets.stream()
                    .filter(ticket -> ticket.getStatus() == TicketStatus.RESOLVED)
                    .filter(ticket -> ticket.getRequestedItem() != null)
                    .mapToLong(ticket -> ticket.getRequestedQuantity() != null
                            ? ticket.getRequestedQuantity().longValue()
                            : 0L)
                    .sum();

            long receivedAssets = assetRepository.countByUserId(userId);
            receivedItemsCount = receivedInventoryItems + receivedAssets;
            
            log.debug("USER {} dashboard: inventory data hidden, receivedItems={}", userId, receivedItemsCount);
        } else {
            // ADMIN e TECHNICIAN visualizam todas as métricas globais de estoque
            totalItems = itemRepository.count();
            lowStockItems = itemRepository.countByCurrentStockLessThanEqual(LOW_STOCK_THRESHOLD);
            outOfStockItems = itemRepository.countByCurrentStockLessThanEqual(OUT_OF_STOCK_THRESHOLD);
        }

        InventorySummaryDTO inventorySummary = new InventorySummaryDTO(
                totalItems,
                lowStockItems,
                outOfStockItems,
                receivedItemsCount
        );

        log.info("Analytics retrieved: open={}, inProgress={}, resolved={}, closed={}, lowStock={}",
                openTickets, inProgressTickets, resolvedTickets, closedTickets, lowStockItems);

        // Total de chamados = soma de todos os status
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
     * Constrói a lista de métricas de chamados agrupados por status.
     */
    private List<MetricDTO> buildTicketsByStatusMetrics(long open, long inProgress, long resolved) {
        return List.of(
                new MetricDTO("Aberto", open),
                new MetricDTO("Em Progresso", inProgress),
                new MetricDTO("Resolvido", resolved)
        );
    }

    /**
     * Constrói a lista de métricas de chamados agrupados por categoria.
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
     * Constrói a lista com o Top 5 de métricas de chamados agrupados por setor.
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
     * Constrói a lista com o Top 5 de métricas de chamados agrupados por solicitante.
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
