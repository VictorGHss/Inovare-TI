package br.dev.ctrls.inovareti.modules.analytics.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.analytics.application.dto.AnalyticsMetricView;
import br.dev.ctrls.inovareti.modules.analytics.application.dto.DashboardAnalyticsDTO;
import br.dev.ctrls.inovareti.modules.analytics.application.dto.InventorySummaryDTO;
import br.dev.ctrls.inovareti.modules.analytics.application.dto.MetricDTO;
import br.dev.ctrls.inovareti.modules.analytics.application.dto.SectorPriorityMetricDTO;
import br.dev.ctrls.inovareti.modules.analytics.application.dto.SectorPriorityMetricView;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: recupera os dados de analítica do dashboard com isolamento por perfil.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetDashboardAnalyticsUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final ItemRepositoryPort itemRepository;
    private final AssetRepositoryPort assetRepository;

    private static final int LOW_STOCK_THRESHOLD = 3;
    private static final int OUT_OF_STOCK_THRESHOLD = 0;

    @Transactional(readOnly = true)
    public DashboardAnalyticsDTO execute(UUID userId, UserRole userRole) {
        log.info("Fetching dashboard analytics data for user: {} (role: {})", userId, userRole);

        boolean restrictToRequester = userRole != UserRole.ADMIN && userRole != UserRole.TECHNICIAN;
        List<Ticket> userTickets = restrictToRequester ? ticketRepository.findByRequesterId(userId) : List.of();

        long openTickets;
        long inProgressTickets;
        long resolvedTickets;

        if (restrictToRequester) {
            openTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.RESOLVED);
        } else {
            openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        }

        long closedTickets = restrictToRequester
                ? ticketRepository.countByRequesterIdAndClosedAtIsNotNull(userId)
                : ticketRepository.countByClosedAtIsNotNull();

        List<MetricDTO> ticketsByStatus = buildTicketsByStatusMetrics(openTickets, inProgressTickets, resolvedTickets);
        List<MetricDTO> ticketsByCategory = buildMetrics(ticketRepository.countTicketsByCategory(restrictToRequester, userId));
        List<MetricDTO> ticketsBySector = buildMetrics(ticketRepository.countTicketsBySector(restrictToRequester, userId));
        List<MetricDTO> ticketsByRequester = buildMetrics(ticketRepository.countTicketsByRequester(restrictToRequester, userId));
        List<SectorPriorityMetricDTO> ticketsBySectorAndPriority = buildSectorPriorityMetrics(
                ticketRepository.countTicketsBySectorAndPriority(restrictToRequester, userId));
        List<MetricDTO> slaBreachesByCategory = buildMetrics(ticketRepository.countSlaBreachesByCategory(restrictToRequester, userId));
        List<MetricDTO> ticketsByMonth = buildMetrics(ticketRepository.countTicketsByMonth(restrictToRequester, userId))
                .stream()
                .map(metric -> new MetricDTO(formatMonthLabel(metric.name()), metric.value()))
                .collect(Collectors.toList());

        long totalItems;
        long lowStockItems;
        long outOfStockItems;
        long receivedItemsCount = 0;

        if (userRole == UserRole.USER) {
            totalItems = 0;
            lowStockItems = 0;
            outOfStockItems = 0;

            long receivedInventoryItems = userTickets.stream()
                    .filter(ticket -> ticket.getStatus() == TicketStatus.RESOLVED)
                    .filter(ticket -> ticket.getRequestedItem() != null)
                    .mapToLong(ticket -> ticket.getRequestedQuantity() != null ? ticket.getRequestedQuantity().longValue() : 0L)
                    .sum();

            long receivedAssets = assetRepository.countByUsersId(userId);
            receivedItemsCount = receivedInventoryItems + receivedAssets;

            log.debug("USER {} dashboard: inventory data hidden, receivedItems={}", userId, receivedItemsCount);
        } else {
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

        log.info("Analytics retrieved: open={}, inProgress={}, resolved={}, closed={}, lowStock={}, monthSeries={}, sectorPrioritySeries={}, slaBreachesSeries={}",
                openTickets, inProgressTickets, resolvedTickets, closedTickets, lowStockItems,
                ticketsByMonth.size(), ticketsBySectorAndPriority.size(), slaBreachesByCategory.size());

        long totalTickets = restrictToRequester ? ticketRepository.countByRequesterId(userId) : ticketRepository.count();

        long totalAssets = assetRepository.count();
        long assetsInUse = assetRepository.countInUse();
        long assetsInStock = assetRepository.countInStock();

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
                ticketsBySectorAndPriority,
                slaBreachesByCategory,
                ticketsByMonth,
                inventorySummary,
                totalAssets,
                assetsInUse,
                assetsInStock
        );
    }

    private List<MetricDTO> buildTicketsByStatusMetrics(long open, long inProgress, long resolved) {
        return List.of(
                new MetricDTO("Aberto", open),
                new MetricDTO("Em Progresso", inProgress),
                new MetricDTO("Resolvido", resolved)
        );
    }

    private List<MetricDTO> buildMetrics(List<AnalyticsMetricView> metrics) {
        return metrics.stream()
                .map(metric -> new MetricDTO(metric.getName(), Objects.requireNonNullElse(metric.getValue(), 0L)))
                .collect(Collectors.toList());
    }

    private List<SectorPriorityMetricDTO> buildSectorPriorityMetrics(List<SectorPriorityMetricView> metrics) {
        return metrics.stream()
                .map(metric -> new SectorPriorityMetricDTO(
                        metric.getSector(),
                        metric.getPriority(),
                        Objects.requireNonNullElse(metric.getValue(), 0L)))
                .collect(Collectors.toList());
    }

    private String formatMonthLabel(String monthKey) {
        if (monthKey == null || monthKey.isBlank()) {
            return "Sem data";
        }

        try {
            java.time.LocalDate parsed = java.time.LocalDate.parse(monthKey);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern(
                    "MMM/yy",
                    java.util.Locale.forLanguageTag("pt-BR"));
            return parsed.format(formatter).toLowerCase(java.util.Locale.ROOT);
        } catch (Exception ignored) {
            return monthKey;
        }
    }
}
