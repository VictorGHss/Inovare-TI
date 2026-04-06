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

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
        return reportExcelExporter.exportTicketsToExcel(tickets);
    }

    public ByteArrayInputStream exportInventoryEntriesToExcel(List<StockBatch> batches) {
        return reportExcelExporter.exportInventoryEntriesToExcel(batches);
    }

    public ByteArrayInputStream exportInventoryExitsToExcel(List<Ticket> tickets) {
        Map<UUID, BigDecimal> totalsByTicket = inventoryPricingService.calculateExitTotalsByTicket(tickets);
        return reportExcelExporter.exportInventoryExitsToExcel(tickets, totalsByTicket);
    }

    public ByteArrayInputStream exportInventoryExitsToPdf(List<Ticket> tickets) {
        Map<UUID, BigDecimal> totalsByTicket = inventoryPricingService.calculateExitTotalsByTicket(tickets);
        return reportPdfExporter.exportInventoryExitsToPdf(tickets, totalsByTicket);
    }
}
