package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
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

    private final br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort stockMovementRepository;
    private final br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort itemRepository;
    private final br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort assetMaintenanceRepository;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort ticketRepository;

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
        return reportExcelExporter.exportTicketsToExcel(tickets);
    }

    public ByteArrayInputStream exportInventoryEntriesToExcel(List<StockBatch> batches, Map<UUID, BigDecimal> periodCosts) {
        return reportExcelExporter.exportInventoryEntriesToExcel(batches, periodCosts);
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

        // -----------------------------------------------------------------------
        // 1) Chamados de SOLICITAÇÃO resolvidos no período (requestedItem preenchido)
        //    Buscados diretamente com todas as relações eager (requester, sector, item)
        // -----------------------------------------------------------------------
        try {
            List<Ticket> requestTickets = ticketRepository.findResolvedRequestTicketsInPeriod(start, end);
            for (Ticket ticket : requestTickets) {
                // Custo: busca o movimento de saída gerado pelo FIFO ao resolver o chamado
                BigDecimal cost = BigDecimal.ZERO;
                try {
                    String refPrefix = "TICKET:" + ticket.getId();
                    var movements = stockMovementRepository.findByReferenceStartingWithAndTypeOrderByDateDesc(
                            refPrefix, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType.OUT);
                    if (movements != null && !movements.isEmpty()) {
                        cost = movements.stream()
                                .map(m -> m.getUnitPriceAtTime() != null ? m.getUnitPriceAtTime() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }
                } catch (Exception e) {
                    // swallow — custo fica ZERO se não encontrar movimento
                }
                totalsByTicket.put(ticket.getId(), cost);
                unifiedRows.add(ticket);
            }
        } catch (Exception e) {
            // swallow
        }

        // -----------------------------------------------------------------------
        // 2) Saídas DIRETAS de consumíveis (StockMovements sem referência TICKET:)
        //    Esses movimentos não possuem chamado vinculado
        // -----------------------------------------------------------------------
        try {
            List<br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement> movements =
                    stockMovementRepository.findByDateBetweenAndTypeOrderByDateDesc(
                            start, end, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType.OUT);

            if (movements != null) {
                for (var mv : movements) {
                    // Pula movimentos que já foram cobertos pelos chamados de solicitação acima
                    if (mv.getReference() != null && mv.getReference().startsWith("TICKET:")) {
                        continue;
                    }

                    var item = itemRepository.findById(mv.getItemId()).orElse(null);
                    if (item != null) {
                        UUID mockId = UUID.randomUUID();

                        Ticket mockTicket = Ticket.builder()
                                .id(mockId)
                                .title("Saída Direta de Material: " + item.getName())
                                .status(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus.RESOLVED)
                                .priority(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority.NORMAL)
                                .requester(null)
                                .assignedTo(null)
                                .category(null)
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
        } catch (Exception e) {
            // swallow
        }

        // -----------------------------------------------------------------------
        // 3) Ativos entregues via AssetMaintenance do tipo TRANSFER no período
        // -----------------------------------------------------------------------
        try {
            List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> maintenances =
                    assetMaintenanceRepository.findByCreatedAtBetweenAndTypeOrderByCreatedAtDesc(
                            start, end, br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance.MaintenanceType.TRANSFER);

            if (maintenances != null) {
                for (var tf : maintenances) {
                    var asset = tf.getAsset();
                    if (asset != null) {
                        UUID mockId = UUID.randomUUID();

                        // Tenta identificar o chamado a partir da descrição (ex.: "[TICKET:uuid]")
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

                        br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemCategory mockCategory =
                                br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemCategory.builder()
                                        .name(catName)
                                        .isConsumable(false)
                                        .build();

                        br.dev.ctrls.inovareti.modules.inventory.domain.model.Item mockItem =
                                br.dev.ctrls.inovareti.modules.inventory.domain.model.Item.builder()
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
                                .status(originalTicket != null ? originalTicket.getStatus() : br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus.RESOLVED)
                                .priority(originalTicket != null ? originalTicket.getPriority() : br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority.NORMAL)
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
        } catch (Exception e) {
            // swallow
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
