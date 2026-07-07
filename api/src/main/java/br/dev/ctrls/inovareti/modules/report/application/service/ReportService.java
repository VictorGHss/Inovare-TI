package br.dev.ctrls.inovareti.modules.report.application.service;

import io.micrometer.observation.annotation.Observed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportExcelExporterPort;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportPdfExporterPort;
import br.dev.ctrls.inovareti.modules.report.application.dto.OutflowReportRowDTO;
import lombok.RequiredArgsConstructor;

/**
 * Fachada pura de relatórios.
 *
 * Responsabilidades desta classe:
 * - receber requisições de exportação;
 * - delegar cálculo de valores;
 * - delegar a geração do arquivo para os Ports correspondentes (Excel/PDF) retornando byte[].
 */
@Service
@RequiredArgsConstructor
@Observed
public class ReportService {

    private final ReportExcelExporterPort reportExcelExporter;
    private final ReportPdfExporterPort reportPdfExporter;

    private final br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort stockMovementRepository;
    private final br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort itemRepository;
    private final br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort assetMaintenanceRepository;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort ticketRepository;

    public byte[] exportTicketsToExcel(List<Ticket> tickets) {
        return reportExcelExporter.exportTicketsToExcel(tickets);
    }

    public byte[] exportInventoryEntriesToExcel(List<StockBatch> batches, Map<UUID, BigDecimal> periodCosts) {
        return reportExcelExporter.exportInventoryEntriesToExcel(batches, periodCosts);
    }

    public byte[] exportInventoryExitsToExcel(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<OutflowReportRowDTO> unifiedRows = buildUnifiedExitRows(start, end);
        return reportExcelExporter.exportInventoryExitsToExcel(unifiedRows);
    }

    public byte[] exportInventoryExitsToPdf(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<OutflowReportRowDTO> unifiedRows = buildUnifiedExitRows(start, end);
        return reportPdfExporter.exportInventoryExitsToPdf(unifiedRows);
    }

    private List<OutflowReportRowDTO> buildUnifiedExitRows(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<OutflowReportRowDTO> unifiedRows = new java.util.ArrayList<>();

        // 1) Chamados de SOLICITAÇÃO resolvidos no período (requestedItem preenchido)
        try {
            List<Ticket> requestTickets = ticketRepository.findResolvedRequestTicketsInPeriod(start, end);
            for (Ticket ticket : requestTickets) {
                BigDecimal cost = BigDecimal.ZERO;
                try {
                    String refPrefix = "TICKET:" + ticket.getId();
                    var movements = stockMovementRepository.findByReferenceStartingWithAndTypeOrderByDateDesc(
                            refPrefix, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType.OUT);
                    if (movements != null && !movements.isEmpty()) {
                        cost = movements.stream()
                                .map(m -> m.getUnitPriceAtTime() != null ? m.getUnitPriceAtTime() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
                    }
                } catch (Exception e) {
                    // Custo fica zero
                }

                String itemType = ticket.getRequestedItem() != null && ticket.getRequestedItem().getItemCategory() != null 
                        ? ticket.getRequestedItem().getItemCategory().getName() 
                        : "-";
                String item = ticket.getRequestedItem() != null ? ticket.getRequestedItem().getName() : "-";
                int qty = Math.abs(Optional.ofNullable(ticket.getRequestedQuantity()).orElse(0));
                String requester = ticket.getRequester() != null ? ticket.getRequester().getName() : "-";
                String location = ticket.getRequester() != null && ticket.getRequester().getLocation() != null ? ticket.getRequester().getLocation() : "-";
                String sector = ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "-";
                LocalDateTime date = ticket.getClosedAt() != null ? ticket.getClosedAt() : ticket.getCreatedAt();

                unifiedRows.add(new OutflowReportRowDTO(itemType, item, qty, requester, location, sector, cost, date));
            }
        } catch (Exception e) {
            // Ignorado
        }

        // 2) Saídas DIRETAS de consumíveis (StockMovements sem referência TICKET:)
        try {
            List<br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement> movements =
                    stockMovementRepository.findByDateBetweenAndTypeOrderByDateDesc(
                            start, end, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType.OUT);

            if (movements != null) {
                for (var mv : movements) {
                    if (mv.getReference() != null && mv.getReference().startsWith("TICKET:")) {
                        continue;
                    }

                    var item = itemRepository.findById(mv.getItemId()).orElse(null);
                    if (item != null) {
                        String itemType = item.getItemCategory() != null ? item.getItemCategory().getName() : "-";
                        String itemName = item.getName();
                        int qty = mv.getQuantity();
                        BigDecimal cost = mv.getUnitPriceAtTime() != null ? mv.getUnitPriceAtTime() : BigDecimal.ZERO;
                        LocalDateTime date = mv.getDate();

                        unifiedRows.add(new OutflowReportRowDTO(itemType, itemName, qty, "-", "-", "-", cost, date));
                    }
                }
            }
        } catch (Exception e) {
            // Ignorado
        }

        // 3) Manutenções de ativos ocorridas no período
        try {
            List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> maintenances =
                    assetMaintenanceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

            if (maintenances != null) {
                for (var tf : maintenances) {
                    var asset = tf.getAsset();
                    if (asset != null) {
                        String itemType = "Ativo - Manutenção";
                        
                        String description = tf.getDescription() != null ? tf.getDescription().trim() : "";
                        String itemText = asset.getName() + " [Patr: " + asset.getPatrimonyCode() + "]";
                        if (!description.isEmpty()) {
                            itemText += " - " + description;
                        }

                        int qty = 1;

                        String requester = "-";
                        if (tf.getTicket() != null && tf.getTicket().getRequester() != null) {
                            requester = tf.getTicket().getRequester().getName();
                        } else if (tf.getTechnician() != null) {
                            requester = tf.getTechnician().getName();
                        }

                        String location = "-";
                        if (tf.getTicket() != null && tf.getTicket().getRequester() != null && tf.getTicket().getRequester().getLocation() != null) {
                            location = tf.getTicket().getRequester().getLocation();
                        } else if (asset.getUsers() != null && !asset.getUsers().isEmpty()) {
                            var firstUser = asset.getUsers().iterator().next();
                            if (firstUser.getLocation() != null) {
                                location = firstUser.getLocation();
                            }
                        } else if (tf.getTechnician() != null && tf.getTechnician().getLocation() != null) {
                            location = tf.getTechnician().getLocation();
                        }

                        String sector = "-";
                        if (tf.getTicket() != null && tf.getTicket().getRequester() != null && tf.getTicket().getRequester().getSector() != null) {
                            sector = tf.getTicket().getRequester().getSector().getName();
                        } else if (asset.getUsers() != null && !asset.getUsers().isEmpty()) {
                            var firstUser = asset.getUsers().iterator().next();
                            if (firstUser.getSector() != null) {
                                sector = firstUser.getSector().getName();
                            }
                        } else if (tf.getTechnician() != null && tf.getTechnician().getSector() != null) {
                            sector = tf.getTechnician().getSector().getName();
                        }

                        BigDecimal cost = tf.getCost() != null ? tf.getCost() : BigDecimal.ZERO;
                        LocalDateTime date = tf.getMaintenanceDate() != null ? tf.getMaintenanceDate().atStartOfDay() : (tf.getCreatedAt() != null ? tf.getCreatedAt() : LocalDateTime.now());

                        unifiedRows.add(new OutflowReportRowDTO(itemType, itemText, qty, requester, location, sector, cost, date));
                    }
                }
            }
        } catch (Exception e) {
            // Ignorado
        }

        // Ordenação estritamente cronológica
        unifiedRows.sort((a, b) -> {
            if (a.deliveryDate() == null && b.deliveryDate() == null) return 0;
            if (a.deliveryDate() == null) return 1;
            if (b.deliveryDate() == null) return -1;
            return a.deliveryDate().compareTo(b.deliveryDate());
        });

        return unifiedRows;
    }
}


