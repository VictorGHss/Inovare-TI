package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import lombok.extern.slf4j.Slf4j;

/**
 * Exportador especializado em relatórios Excel.
 *
 * Toda a lógica de geração de planilhas e formatação de células
 * foi movida para este componente.
 */
@Component
@Slf4j
public class ReportExcelExporter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
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
                row.createCell(2).setCellValue(ticket.getRequester().getName());
                row.createCell(3).setCellValue(ticket.getRequester().getSector().getName());
                row.createCell(4).setCellValue(ticket.getCategory().getName());
                row.createCell(5).setCellValue(ticket.getStatus().toString());
                row.createCell(6).setCellValue(ticket.getPriority().toString());
                row.createCell(7).setCellValue(ticket.getCreatedAt().format(DATE_FORMATTER));
                row.createCell(8).setCellValue(ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : "");
            }

            autoSizeColumns(sheet, headers.length);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for tickets", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    public ByteArrayInputStream exportInventoryEntriesToExcel(List<StockBatch> batches, Map<UUID, BigDecimal> periodCosts) {
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

                row.createCell(0).setCellValue(batch.getItem().getItemCategory().getName());
                row.createCell(1).setCellValue(batch.getItem().getName());
                row.createCell(2).setCellValue(batch.getBrand() != null ? batch.getBrand() : "-");
                row.createCell(3).setCellValue(batch.getOriginalQuantity());
                row.createCell(4).setCellValue(batch.getSupplier() != null ? batch.getSupplier() : "-");
                row.createCell(5).setCellValue(CURRENCY_FORMATTER.format(batch.getUnitPrice()));

                BigDecimal totalPrice = periodCosts.getOrDefault(batch.getId(), BigDecimal.ZERO);
                row.createCell(6).setCellValue(CURRENCY_FORMATTER.format(totalPrice));

                row.createCell(7).setCellValue(batch.getPurchaseReason() != null ? batch.getPurchaseReason() : "-");
                row.createCell(8).setCellValue(batch.getEntryDate().format(DATE_FORMATTER));
            }

            autoSizeColumns(sheet, headers.length);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for inventory entries", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    public ByteArrayInputStream exportInventoryExitsToExcel(
            List<Ticket> tickets,
            Map<UUID, BigDecimal> totalsByTicket) {
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
                if (ticket.getStatus().toString().equals("RESOLVED")
                        && ticket.getRequestedItem() != null
                        && ticket.getRequestedQuantity() != null) {

                    Row row = sheet.createRow(rowNum++);

                    int qty = Optional.ofNullable(ticket.getRequestedQuantity()).orElse(0);
                    qty = Math.abs(qty); // Garante quantidade positiva para saídas na planilha

                    row.createCell(0).setCellValue(ticket.getRequestedItem().getItemCategory().getName());
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
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for inventory exits", e);
            throw new RuntimeException("Failed to generate Excel report", e);
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
