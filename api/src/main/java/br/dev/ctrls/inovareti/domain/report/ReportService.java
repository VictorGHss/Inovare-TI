package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.inventory.StockBatch;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;

/**
 * Fachada de relatórios.
 *
 * Responsabilidades desta classe:
 * - receber requisições de exportação;
 * - delegar cálculo de valores ao InventoryPricingService;
 * - delegar a geração do arquivo ao exportador apropriado (Excel/PDF).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportExcelExporter reportExcelExporter;
    private final ReportPdfExporter reportPdfExporter;
    private final InventoryPricingService inventoryPricingService;

    private final br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository stockMovementRepository;
    private final br.dev.ctrls.inovareti.domain.inventory.ItemRepository itemRepository;
    private final br.dev.ctrls.inovareti.domain.asset.AssetMaintenanceRepository assetMaintenanceRepository;

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
        return reportExcelExporter.exportTicketsToExcel(tickets);
    }

    public ByteArrayInputStream exportInventoryEntriesToExcel(List<StockBatch> batches) {
        return reportExcelExporter.exportInventoryEntriesToExcel(batches);
    }

    public ByteArrayInputStream exportInventoryExitsToExcel(List<Ticket> tickets) {
        java.util.Map<UUID, BigDecimal> totalsByTicket = new java.util.HashMap<>();
        List<Ticket> unifiedTickets = buildUnifiedExitRows(tickets, totalsByTicket);
        return reportExcelExporter.exportInventoryExitsToExcel(unifiedTickets, totalsByTicket);
    }

    public ByteArrayInputStream exportInventoryExitsToPdf(List<Ticket> tickets) {
        java.util.Map<UUID, BigDecimal> totalsByTicket = new java.util.HashMap<>();
        List<Ticket> unifiedTickets = buildUnifiedExitRows(tickets, totalsByTicket);
        return reportPdfExporter.exportInventoryExitsToPdf(unifiedTickets, totalsByTicket);
    }

    private List<Ticket> buildUnifiedExitRows(List<Ticket> tickets, Map<UUID, BigDecimal> totalsByTicket) {
        List<Ticket> unifiedRows = new java.util.ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ticket == null || ticket.getId() == null) {
                continue;
            }

            if (ticket.getStatus() != br.dev.ctrls.inovareti.domain.ticket.TicketStatus.RESOLVED) {
                continue;
            }

            // 1) Busca movimentos de estoque (consumíveis entregues) vinculados a este ticket
            List<br.dev.ctrls.inovareti.domain.inventory.StockMovement> movements = List.of();
            try {
                movements = stockMovementRepository.findByReferenceContainingIgnoreCaseAndTypeOrderByDateDesc(ticket.getId().toString(), br.dev.ctrls.inovareti.domain.inventory.StockMovementType.OUT);
            } catch (Exception e) {
                // swallow
            }

            // 2) Busca ativos (equipamentos) e manutenções vinculados a este ticket
            List<br.dev.ctrls.inovareti.domain.asset.AssetMaintenance> maintenances = List.of();
            try {
                maintenances = assetMaintenanceRepository.findByDescriptionContainingIgnoreCase(ticket.getId().toString());
            } catch (Exception e) {
                // swallow
            }

            boolean hasExits = (movements != null && !movements.isEmpty()) || (maintenances != null && !maintenances.isEmpty());

            if (!hasExits) {
                // Fallback para chamados antigos que não possuem movimentos de estoque ou transferências no banco:
                if (ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
                    BigDecimal legacyPrice = inventoryPricingService.calculateExitTotalPrice(ticket, ticket.getRequestedQuantity());
                    totalsByTicket.put(ticket.getId(), legacyPrice);
                    unifiedRows.add(ticket);
                }
            } else {
                // Adiciona linhas virtuais de consumíveis para cada movimento real de estoque
                if (movements != null) {
                    for (var mv : movements) {
                        var item = itemRepository.findById(mv.getItemId()).orElse(null);
                        if (item != null) {
                            UUID mockId = UUID.randomUUID();
                            Ticket mockTicket = Ticket.builder()
                                    .id(mockId)
                                    .title(ticket.getTitle())
                                    .status(ticket.getStatus())
                                    .priority(ticket.getPriority())
                                    .requester(ticket.getRequester())
                                    .assignedTo(ticket.getAssignedTo())
                                    .category(ticket.getCategory())
                                    .closedAt(mv.getDate())
                                    .requestedItem(item)
                                    .requestedQuantity(mv.getQuantity())
                                    .build();
                            
                            BigDecimal cost = mv.getUnitPriceAtTime() != null ? mv.getUnitPriceAtTime() : BigDecimal.ZERO;
                            totalsByTicket.put(mockId, cost);
                            
                            unifiedRows.add(mockTicket);
                        }
                    }
                }

                // Adiciona linhas virtuais para cada manutenção ou ativo entregue
                if (maintenances != null) {
                    for (var tf : maintenances) {
                        var asset = tf.getAsset();
                        if (asset != null) {
                            UUID mockId = UUID.randomUUID();
                            
                            // Determina o nome descritivo da categoria conforme o tipo de atividade
                            String catName = "Ativo - Outros";
                            if (tf.getType() == br.dev.ctrls.inovareti.domain.asset.AssetMaintenance.MaintenanceType.TRANSFER) {
                                catName = "Ativo - Entrega";
                            } else if (tf.getType() == br.dev.ctrls.inovareti.domain.asset.AssetMaintenance.MaintenanceType.PREVENTIVE) {
                                catName = "Ativo - Manut. Preventiva";
                            } else if (tf.getType() == br.dev.ctrls.inovareti.domain.asset.AssetMaintenance.MaintenanceType.CORRECTIVE) {
                                catName = "Ativo - Manut. Corretiva";
                            } else if (tf.getType() == br.dev.ctrls.inovareti.domain.asset.AssetMaintenance.MaintenanceType.UPGRADE) {
                                catName = "Ativo - Upgrade";
                            }
                            
                            br.dev.ctrls.inovareti.domain.inventory.ItemCategory mockCategory = 
                                    br.dev.ctrls.inovareti.domain.inventory.ItemCategory.builder()
                                            .name(catName)
                                            .isConsumable(false)
                                            .build();
                            
                            br.dev.ctrls.inovareti.domain.inventory.Item mockItem = 
                                    br.dev.ctrls.inovareti.domain.inventory.Item.builder()
                                            .name(asset.getName() + " [Patr: " + asset.getPatrimonyCode() + "]")
                                            .itemCategory(mockCategory)
                                            .currentStock(1)
                                            .build();

                            Ticket mockTicket = Ticket.builder()
                                    .id(mockId)
                                    .title(ticket.getTitle())
                                    .status(ticket.getStatus())
                                    .priority(ticket.getPriority())
                                    .requester(ticket.getRequester())
                                    .assignedTo(ticket.getAssignedTo())
                                    .category(ticket.getCategory())
                                    .closedAt(tf.getCreatedAt())
                                    .requestedItem(mockItem)
                                    .requestedQuantity(1)
                                    .build();

                            // Mapeia o custo real da manutenção ou atividade
                            BigDecimal cost = tf.getCost() != null ? tf.getCost() : BigDecimal.ZERO;
                            totalsByTicket.put(mockId, cost);

                            unifiedRows.add(mockTicket);
                        }
                    }
                }
            }
        }

        // Ordenação por data mais antiga até mais nova (conforme solicitado pelo usuário!)
        unifiedRows.sort((a, b) -> {
            if (a.getClosedAt() == null && b.getClosedAt() == null) return 0;
            if (a.getClosedAt() == null) return 1;
            if (b.getClosedAt() == null) return -1;
            return a.getClosedAt().compareTo(b.getClosedAt());
        });

        return unifiedRows;
    }
}
