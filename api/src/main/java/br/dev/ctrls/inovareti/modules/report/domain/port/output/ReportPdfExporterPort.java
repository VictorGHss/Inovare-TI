package br.dev.ctrls.inovareti.modules.report.domain.port.output;

import java.util.List;

/**
 * Porta de saída pura Java para geração física de documentos PDF.
 * A camada de aplicação solicita apenas um byte[] genérico, sem saber
 * a biblioteca de implementação física.
 */
public interface ReportPdfExporterPort {
    byte[] exportInventoryExitsToPdf(List<br.dev.ctrls.inovareti.modules.report.application.dto.OutflowReportRowDTO> rows);
}
