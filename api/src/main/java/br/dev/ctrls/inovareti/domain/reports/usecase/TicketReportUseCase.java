package br.dev.ctrls.inovareti.domain.reports.usecase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.reports.dto.TicketReportDTO;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case: generates an Excel file with ticket report data.
 * Applies tenant isolation (USER sees only their tickets, ADMIN sees all).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReportUseCase {

    private final TicketRepository ticketRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Generates an Excel file with ticket data.
     *
     * @param userId the authenticated user's ID
     * @param userRole the authenticated user's role
     * @return ByteArrayInputStream containing the Excel file
     * @throws IOException if error occurs during file generation
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream generateTicketReport(UUID userId, UserRole userRole) throws IOException {
        log.info("Generating ticket report for user: {} (role: {})", userId, userRole);

        List<Ticket> tickets;
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN and TECHNICIAN see all tickets
            tickets = ticketRepository.findAll();
        } else {
            // USER sees only their own tickets
            tickets = ticketRepository.findByRequesterId(userId);
        }

        List<TicketReportDTO> reportData = tickets.stream()
                .map(this::mapTicketToReport)
                .collect(Collectors.toList());

        return generateExcelFile(reportData);
    }

    /**
     * Converts a Ticket entity to TicketReportDTO.
     */
    private TicketReportDTO mapTicketToReport(Ticket ticket) {
        return new TicketReportDTO(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getRequester().getName(),
                ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "N/A",
                ticket.getStatus().toString(),
                ticket.getCreatedAt(),
                ticket.getClosedAt()
        );
    }

    /**
     * Creates an Excel workbook with ticket report data.
     */
    private ByteArrayInputStream generateExcelFile(List<TicketReportDTO> reports) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Relatório de Chamados");

            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Create header row
            String[] headers = {"ID", "Título", "Solicitante", "Setor", "Status", "Criado Em", "Resolvido Em"};
            var headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Add data rows
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

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
        }

        log.info("Excel report generated successfully with {} tickets", reports.size());
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Creates a header style for Excel cells (bold background).
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor((short) 15);
        style.setFont(font);

        // Light gray background
        style.setFillForegroundColor((short) 8);
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        return style;
    }
}
