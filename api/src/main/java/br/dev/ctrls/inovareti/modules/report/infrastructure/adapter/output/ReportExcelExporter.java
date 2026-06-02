package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.report.application.dto.TicketReportDTO;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportExcelExporterPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de infraestrutura especializado em relatórios Excel físicos.
 * Implementa o contrato puro Java da camada de domínio utilizando o Apache POI.
 */
@Component
@Slf4j
public class ReportExcelExporter implements ReportExcelExporterPort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    @Override
    public byte[] exportTicketsToExcel(List<Ticket> tickets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Chamados");

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "ID", "Título", "Solicitante", "Setor", "Categoria", "Status", "Prioridade", "Criado Em", "Resolvido Em"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Ticket ticket : tickets) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(ticket.getId().toString());
                row.createCell(1).setCellValue(ticket.getTitle());
                row.createCell(2).setCellValue(ticket.getRequester() != null ? ticket.getRequester().getName() : "-");
                row.createCell(3).setCellValue(ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "-");
                row.createCell(4).setCellValue(ticket.getCategory() != null ? ticket.getCategory().getName() : "-");
                row.createCell(5).setCellValue(ticket.getStatus() != null ? ticket.getStatus().toString() : "-");
                row.createCell(6).setCellValue(ticket.getPriority() != null ? ticket.getPriority().toString() : "-");
                row.createCell(7).setCellValue(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FORMATTER) : "");
                row.createCell(8).setCellValue(ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : "");
            }

            autoSizeColumns(sheet, headers.length);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar relatório Excel para chamados", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    @Override
    public byte[] exportInventoryEntriesToExcel(List<StockBatch> batches, Map<UUID, BigDecimal> periodCosts) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Entradas");

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Tipo de Item", "Item", "Marca", "Qtd Adquirida", "Fornecedor", "Preço Un.", "Preço Total", "Motivo da Compra", "Data de Entrada"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (StockBatch batch : batches) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(batch.getItem() != null && batch.getItem().getItemCategory() != null ? batch.getItem().getItemCategory().getName() : "-");
                row.createCell(1).setCellValue(batch.getItem() != null ? batch.getItem().getName() : "-");
                row.createCell(2).setCellValue(batch.getBrand() != null ? batch.getBrand() : "-");
                row.createCell(3).setCellValue(batch.getOriginalQuantity());
                row.createCell(4).setCellValue(batch.getSupplier() != null ? batch.getSupplier() : "-");
                row.createCell(5).setCellValue(CURRENCY_FORMATTER.format(batch.getUnitPrice()));

                BigDecimal totalPrice = periodCosts.getOrDefault(batch.getId(), BigDecimal.ZERO);
                row.createCell(6).setCellValue(CURRENCY_FORMATTER.format(totalPrice));

                row.createCell(7).setCellValue(batch.getPurchaseReason() != null ? batch.getPurchaseReason() : "-");
                row.createCell(8).setCellValue(batch.getEntryDate() != null ? batch.getEntryDate().format(DATE_FORMATTER) : "");
            }

            autoSizeColumns(sheet, headers.length);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar relatório Excel para entradas de estoque", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    @Override
    public byte[] exportInventoryExitsToExcel(List<Ticket> tickets, Map<UUID, BigDecimal> totalsByTicket) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Saídas");

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Tipo de Item", "Item", "Qtd Entregue", "Quem Solicitou", "Local do Usuário", "Setor do Usuário", "Preço Total", "Data da Entrega"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Ticket ticket : tickets) {
                if (ticket.getStatus() != null && ticket.getStatus().toString().equals("RESOLVED")
                        && ticket.getRequestedItem() != null
                        && ticket.getRequestedQuantity() != null) {

                    Row row = sheet.createRow(rowNum++);

                    int qty = Optional.ofNullable(ticket.getRequestedQuantity()).orElse(0);
                    qty = Math.abs(qty);

                    row.createCell(0).setCellValue(ticket.getRequestedItem().getItemCategory() != null ? ticket.getRequestedItem().getItemCategory().getName() : "-");
                    row.createCell(1).setCellValue(ticket.getRequestedItem().getName());
                    row.createCell(2).setCellValue(qty);
                    row.createCell(3).setCellValue(ticket.getRequester() != null ? ticket.getRequester().getName() : "-");
                    row.createCell(4).setCellValue(ticket.getRequester() != null && ticket.getRequester().getLocation() != null ? ticket.getRequester().getLocation() : "-");
                    row.createCell(5).setCellValue(ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "-");

                    BigDecimal totalPrice = totalsByTicket.getOrDefault(ticket.getId(), BigDecimal.ZERO);
                    row.createCell(6).setCellValue(CURRENCY_FORMATTER.format(totalPrice));

                    row.createCell(7).setCellValue(ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : "");
                }
            }

            autoSizeColumns(sheet, headers.length);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar relatório Excel para saídas de estoque", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    @Override
    public byte[] generateTicketReport(List<TicketReportDTO> reports) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Relatório de Chamados");

            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {"ID", "Título", "Solicitante", "Setor", "Status", "Criado Em", "Resolvido Em"};
            var headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (TicketReportDTO report : reports) {
                var row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(report.id().toString());
                row.createCell(1).setCellValue(report.title());
                row.createCell(2).setCellValue(report.requesterName());
                row.createCell(3).setCellValue(report.sectorName());
                row.createCell(4).setCellValue(report.status());
                row.createCell(5).setCellValue(report.createdAt() != null ? report.createdAt().format(DATE_FORMATTER) : "");
                row.createCell(6).setCellValue(report.resolvedAt() != null ? report.resolvedAt().format(DATE_FORMATTER) : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar relatório de chamados personalizado em Excel", e);
            throw new RuntimeException("Erro ao gerar relatório em Excel", e);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        return headerStyle;
    }
}
