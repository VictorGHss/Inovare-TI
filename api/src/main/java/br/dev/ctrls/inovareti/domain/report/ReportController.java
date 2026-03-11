package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.inventory.StockBatch;
import br.dev.ctrls.inovareti.domain.inventory.StockBatchRepository;
import br.dev.ctrls.inovareti.domain.reports.usecase.TicketReportUseCase;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final TicketRepository ticketRepository;
    private final StockBatchRepository stockBatchRepository;
    private final TicketReportUseCase ticketReportUseCase;
    private final UserRepository userRepository;

    public ReportController(
            ReportService reportService,
            TicketRepository ticketRepository,
            StockBatchRepository stockBatchRepository,
            TicketReportUseCase ticketReportUseCase,
            UserRepository userRepository) {
        this.reportService = reportService;
        this.ticketRepository = ticketRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.ticketReportUseCase = ticketReportUseCase;
        this.userRepository = userRepository;
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<InputStreamResource> exportTickets() {
        List<Ticket> tickets = ticketRepository.findAll();
        ByteArrayInputStream excelFile = reportService.exportTicketsToExcel(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chamados.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }

    /**
     * GET /api/reports/tickets/export
     * Gera relatório de chamados em Excel com isolamento por perfil de usuário.
     * USER exporta apenas seus chamados; ADMIN e TECHNICIAN exportam todos.
     */
    @GetMapping("/tickets/export")
    public ResponseEntity<InputStreamResource> exportTicketsReport() throws IOException {
        log.info("GET /api/reports/tickets/export - Exportando relatório de chamados");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;
        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Não foi possível identificar o usuário autenticado");
            return ResponseEntity.badRequest().build();
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        ByteArrayInputStream stream = ticketReportUseCase.generateTicketReport(userId, user.getRole());

        String filename = "relatorio_chamados_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/inventory/entries")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<InputStreamResource> exportInventoryEntries() {
        List<StockBatch> batches = stockBatchRepository.findAll();
        ByteArrayInputStream excelFile = reportService.exportInventoryEntriesToExcel(batches);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=entradas_estoque.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }

    @GetMapping("/inventory/exits")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<InputStreamResource> exportInventoryExits() {
        List<Ticket> tickets = ticketRepository.findAll();
        ByteArrayInputStream excelFile = reportService.exportInventoryExitsToExcel(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=saidas_estoque.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }
}
