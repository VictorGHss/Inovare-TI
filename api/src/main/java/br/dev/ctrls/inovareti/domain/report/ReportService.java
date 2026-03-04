package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Chamados");

            // Create header style
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

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "Título", "Solicitante", "Setor", "Categoria", "Status", "Prioridade", "Criado Em", "Resolvido Em"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fill data rows
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

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for tickets", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
}
