package br.dev.ctrls.inovareti.domain.analytics.usecase;

import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView;
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

        boolean restrictToRequester = userRole != UserRole.ADMIN && userRole != UserRole.TECHNICIAN;
                List<Ticket> userTickets = restrictToRequester ? ticketRepository.findByRequesterId(userId) : List.of();

        long openTickets;
        long inProgressTickets;
        long resolvedTickets;

        if (!restrictToRequester) {
            // ADMIN e TECHNICIAN visualizam todos os chamados
            openTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        } else {
            // USER visualiza apenas seus próprios chamados
            openTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.OPEN);
            inProgressTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.IN_PROGRESS);
            resolvedTickets = ticketRepository.countByRequesterIdAndStatus(userId, TicketStatus.RESOLVED);
        }

        long closedTickets = restrictToRequester
                ? ticketRepository.countByRequesterIdAndClosedAtIsNotNull(userId)
                : ticketRepository.countByClosedAtIsNotNull();

        // Agrega métricas de chamados por status
        List<MetricDTO> ticketsByStatus = buildTicketsByStatusMetrics(openTickets, inProgressTickets, resolvedTickets);

        // Agrega métricas de chamados por categoria
        List<MetricDTO> ticketsByCategory = buildMetrics(
                ticketRepository.countTicketsByCategory(restrictToRequester, userId));

        // Agrega métricas de chamados por setor (Top 5)
        List<MetricDTO> ticketsBySector = buildMetrics(
                ticketRepository.countTicketsBySector(restrictToRequester, userId));

        // Agrega métricas de chamados por solicitante (Top 5)
        List<MetricDTO> ticketsByRequester = buildMetrics(
                ticketRepository.countTicketsByRequester(restrictToRequester, userId));

        // Agrega série mensal de chamados para gráficos temporais
        List<MetricDTO> ticketsByMonth = buildMetrics(
                ticketRepository.countTicketsByMonth(restrictToRequester, userId))
                .stream()
                .map(metric -> new MetricDTO(formatMonthLabel(metric.name()), metric.value()))
                .collect(Collectors.toList());

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

                        long receivedInventoryItems = userTickets.stream()
                    .filter(ticket -> ticket.getStatus() == TicketStatus.RESOLVED)
                    .filter(ticket -> ticket.getRequestedItem() != null)
                    .mapToLong(ticket -> ticket.getRequestedQuantity() != null
                            ? ticket.getRequestedQuantity().longValue()
                            : 0L)
                    .sum();

            long receivedAssets = assetRepository.countByUsersId(userId);
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

        log.info("Analytics retrieved: open={}, inProgress={}, resolved={}, closed={}, lowStock={}, monthSeries={}",
                openTickets, inProgressTickets, resolvedTickets, closedTickets, lowStockItems, ticketsByMonth.size());

        // Total de chamados = soma de todos os status
        long totalTickets = restrictToRequester ? ticketRepository.countByRequesterId(userId) : ticketRepository.count();

        // Agrega métricas de ativos físicos
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
                ticketsByMonth,
                inventorySummary,
                totalAssets,
                assetsInUse,
                assetsInStock
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

        private List<MetricDTO> buildMetrics(List<AnalyticsMetricView> metrics) {
                return metrics.stream()
                                .map(metric -> {
                                        long safeValue = Objects.requireNonNullElse(metric.getValue(), 0L);
                                        return new MetricDTO(metric.getName(), safeValue);
                                })
                                .collect(Collectors.toList());
        }

        private String formatMonthLabel(String monthKey) {
                if (monthKey == null || monthKey.isBlank()) {
                        return "Sem data";
                }

                try {
                        java.time.LocalDate parsed = java.time.LocalDate.parse(monthKey);
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM/yy", java.util.Locale.forLanguageTag("pt-BR"));
                        return parsed.format(formatter).toLowerCase(java.util.Locale.ROOT);
                } catch (Exception ignored) {
                        return monthKey;
                }
    }
}
