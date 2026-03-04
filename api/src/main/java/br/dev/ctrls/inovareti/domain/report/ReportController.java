package br.dev.ctrls.inovareti.domain.report;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;

import java.io.ByteArrayInputStream;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final TicketRepository ticketRepository;

    public ReportController(ReportService reportService, TicketRepository ticketRepository) {
        this.reportService = reportService;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<InputStreamResource> exportTickets() {
        List<Ticket> tickets = ticketRepository.findAll();
        ByteArrayInputStream excelFile = reportService.exportTicketsToExcel(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chamados.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        InputStreamResource resource = new InputStreamResource(excelFile);
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
