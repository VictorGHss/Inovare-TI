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

    private final br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository stockMovementRepository;
    private final br.dev.ctrls.inovareti.domain.inventory.ItemRepository itemRepository;
    private final br.dev.ctrls.inovareti.domain.asset.AssetMaintenanceRepository assetMaintenanceRepository;
    private final br.dev.ctrls.inovareti.domain.ticket.TicketRepository ticketRepository;

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
        return reportExcelExporter.exportTicketsToExcel(tickets);
    }

    public ByteArrayInputStream exportInventoryEntriesToExcel(List<StockBatch> batches) {
        return reportExcelExporter.exportInventoryEntriesToExcel(batches);
    }

    public ByteArrayInputStream exportInventoryExitsToExcel(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        java.util.Map<UUID, BigDecimal> totalsByTicket = new java.util.HashMap<>();
        List<Ticket> unifiedTickets = buildUnifiedExitRows(start, end, totalsByTicket);
        return reportExcelExporter.exportInventoryExitsToExcel(unifiedTickets, totalsByTicket);
    }

    public ByteArrayInputStream exportInventoryExitsToPdf(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        java.util.Map<UUID, BigDecimal> totalsByTicket = new java.util.HashMap<>();
        List<Ticket> unifiedTickets = buildUnifiedExitRows(start, end, totalsByTicket);
        return reportPdfExporter.exportInventoryExitsToPdf(unifiedTickets, totalsByTicket);
    }

    private List<Ticket> buildUnifiedExitRows(java.time.LocalDateTime start, java.time.LocalDateTime end, Map<UUID, BigDecimal> totalsByTicket) {
        List<Ticket> unifiedRows = new java.util.ArrayList<>();

        // 1) Busca movimentos de estoque (consumíveis entregues) no intervalo
        List<br.dev.ctrls.inovareti.domain.inventory.StockMovement> movements = List.of();
        try {
            movements = stockMovementRepository.findByDateBetweenAndTypeOrderByDateDesc(
                start, end, br.dev.ctrls.inovareti.domain.inventory.StockMovementType.OUT);
        } catch (Exception e) {
            // swallow
        }

        // 2) Busca ativos (equipamentos) e manutenções de entrega no intervalo
        List<br.dev.ctrls.inovareti.domain.asset.AssetMaintenance> maintenances = List.of();
        try {
            maintenances = assetMaintenanceRepository.findByCreatedAtBetweenAndTypeOrderByCreatedAtDesc(
                start, end, br.dev.ctrls.inovareti.domain.asset.AssetMaintenance.MaintenanceType.TRANSFER);
        } catch (Exception e) {
            // swallow
        }

        // 3) Adiciona linhas virtuais de consumíveis para cada movimento real de estoque
        if (movements != null) {
            for (var mv : movements) {
                var item = itemRepository.findById(mv.getItemId()).orElse(null);
                if (item != null) {
                    UUID mockId = UUID.randomUUID();
                    
                    // Tenta identificar o chamado a partir da referência (ex.: "TICKET:uuid")
                    Ticket originalTicket = null;
                    if (mv.getReference() != null && mv.getReference().startsWith("TICKET:")) {
                        try {
                            String ticketIdStr = mv.getReference().substring(7).trim();
                            UUID ticketId = UUID.fromString(ticketIdStr);
                            originalTicket = ticketRepository.findByIdWithRelations(ticketId).orElse(null);
                        } catch (Exception e) {
                            // swallow
                        }
                    }

                    Ticket mockTicket = Ticket.builder()
                            .id(mockId)
                            .title(originalTicket != null ? originalTicket.getTitle() : "Saída Direta de Material: " + item.getName())
                            .status(originalTicket != null ? originalTicket.getStatus() : br.dev.ctrls.inovareti.domain.ticket.TicketStatus.RESOLVED)
                            .priority(originalTicket != null ? originalTicket.getPriority() : br.dev.ctrls.inovareti.domain.ticket.TicketPriority.NORMAL)
                            .requester(originalTicket != null ? originalTicket.getRequester() : null)
                            .assignedTo(originalTicket != null ? originalTicket.getAssignedTo() : null)
                            .category(originalTicket != null ? originalTicket.getCategory() : null)
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

        // 4) Adiciona linhas virtuais para cada manutenção ou ativo entregue
        if (maintenances != null) {
            for (var tf : maintenances) {
                var asset = tf.getAsset();
                if (asset != null) {
                    UUID mockId = UUID.randomUUID();
                    
                    // Tenta identificar o chamado a partir da descrição (ex.: contendo "[TICKET:uuid]")
                    Ticket originalTicket = null;
                    if (tf.getDescription() != null && tf.getDescription().contains("TICKET:")) {
                        try {
                            String desc = tf.getDescription();
                            int startIdx = desc.indexOf("TICKET:") + 7;
                            int endIdx = desc.indexOf("]", startIdx);
                            if (endIdx > startIdx) {
                                String ticketIdStr = desc.substring(startIdx, endIdx).trim();
                                UUID ticketId = UUID.fromString(ticketIdStr);
                                originalTicket = ticketRepository.findByIdWithRelations(ticketId).orElse(null);
                            }
                        } catch (Exception e) {
                            // swallow
                        }
                    }

                    // Determina o nome descritivo da categoria conforme o tipo de atividade
                    String catName = "Ativo - Outros";
                    if (null != tf.getType()) switch (tf.getType()) {
                        case TRANSFER -> catName = "Ativo - Entrega";
                        case PREVENTIVE -> catName = "Ativo - Manut. Preventiva";
                        case CORRECTIVE -> catName = "Ativo - Manut. Corretiva";
                        case UPGRADE -> catName = "Ativo - Upgrade";
                        default -> {
                        }
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

                    // Se não tiver chamado original, tenta obter o solicitante a partir do primeiro usuário do ativo
                    br.dev.ctrls.inovareti.domain.user.User recipient = null;
                    if (originalTicket != null) {
                        recipient = originalTicket.getRequester();
                    } else if (asset.getUsers() != null && !asset.getUsers().isEmpty()) {
                        recipient = asset.getUsers().iterator().next();
                    }

                    Ticket mockTicket = Ticket.builder()
                            .id(mockId)
                            .title(originalTicket != null ? originalTicket.getTitle() : "Entrega Direta de Ativo: " + asset.getName())
                            .status(originalTicket != null ? originalTicket.getStatus() : br.dev.ctrls.inovareti.domain.ticket.TicketStatus.RESOLVED)
                            .priority(originalTicket != null ? originalTicket.getPriority() : br.dev.ctrls.inovareti.domain.ticket.TicketPriority.NORMAL)
                            .requester(recipient)
                            .assignedTo(originalTicket != null ? originalTicket.getAssignedTo() : tf.getTechnician())
                            .category(originalTicket != null ? originalTicket.getCategory() : null)
                            .closedAt(tf.getCreatedAt())
                            .requestedItem(mockItem)
                            .requestedQuantity(1)
                            .build();

                    BigDecimal cost = tf.getCost() != null ? tf.getCost() : BigDecimal.ZERO;
                    totalsByTicket.put(mockId, cost);

                    unifiedRows.add(mockTicket);
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
