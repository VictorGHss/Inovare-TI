package br.dev.ctrls.inovareti.modules.report.domain.port.output;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.report.application.dto.TicketReportDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de saída pura Java para geração física de planilhas Excel.
 * A camada de aplicação solicita apenas um byte[] genérico, sem saber
 * a biblioteca de implementação física.
 */
public interface ReportExcelExporterPort {
    byte[] exportTicketsToExcel(List<Ticket> tickets);
    byte[] exportInventoryEntriesToExcel(List<StockBatch> batches, Map<UUID, BigDecimal> periodCosts);
    byte[] exportInventoryExitsToExcel(List<Ticket> tickets, Map<UUID, BigDecimal> totalsByTicket);
    byte[] generateTicketReport(List<TicketReportDTO> reports);
}
