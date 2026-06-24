package br.dev.ctrls.inovareti.modules.report.domain.port.output;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de saída pura Java para geração física de documentos PDF.
 * A camada de aplicação solicita apenas um byte[] genérico, sem saber
 * a biblioteca de implementação física.
 */
public interface ReportPdfExporterPort {
    byte[] exportInventoryExitsToPdf(List<Ticket> tickets, Map<UUID, BigDecimal> totalsByTicket);
    byte[] exportAssetMaintenancesToPdf(List<Object[]> consolidation, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
