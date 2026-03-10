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
 * Caso de uso: gera um arquivo Excel com os dados do relatório de chamados.
 * Aplica isolamento por perfil (USER vê apenas seus chamados, ADMIN vê todos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReportUseCase {

    private final TicketRepository ticketRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Gera um arquivo Excel com os dados dos chamados.
     *
     * @param userId o ID do usuário autenticado
     * @param userRole o perfil do usuário autenticado
     * @return ByteArrayInputStream contendo o arquivo Excel
     * @throws IOException se ocorrer erro durante a geração do arquivo
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream generateTicketReport(UUID userId, UserRole userRole) throws IOException {
        log.info("Generating ticket report for user: {} (role: {})", userId, userRole);

        List<Ticket> tickets;
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN e TECHNICIAN visualizam todos os chamados
            tickets = ticketRepository.findAll();
        } else {
            // USER visualiza apenas seus próprios chamados
            tickets = ticketRepository.findByRequesterId(userId);
        }

        List<TicketReportDTO> reportData = tickets.stream()
                .map(this::mapTicketToReport)
                .collect(Collectors.toList());

        return generateExcelFile(reportData);
    }

    /**
     * Converte uma entidade Ticket em TicketReportDTO.
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
     * Cria um workbook Excel com os dados do relatório de chamados.
     */
    private ByteArrayInputStream generateExcelFile(List<TicketReportDTO> reports) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Relatório de Chamados");

            // Cria o estilo do cabeçalho
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Cria a linha de cabeçalho
            String[] headers = {"ID", "Título", "Solicitante", "Setor", "Status", "Criado Em", "Resolvido Em"};
            var headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Adiciona as linhas de dados
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

            // Ajusta automaticamente a largura das colunas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
        }

        log.info("Excel report generated successfully with {} tickets", reports.size());
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Cria o estilo de cabeçalho para células Excel (fundo em negrito).
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor((short) 15);
        style.setFont(font);

        // Fundo cinza claro
        style.setFillForegroundColor((short) 8);
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        return style;
    }
}
