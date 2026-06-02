package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.input;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockBatchRepositoryPort;
import br.dev.ctrls.inovareti.modules.report.application.service.ReportService;
import br.dev.ctrls.inovareti.modules.report.application.service.TicketReportUseCase;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para endpoints relacionados a relatórios.
 */
@Slf4j
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final TicketRepositoryPort ticketRepository;
    private final StockBatchRepositoryPort stockBatchRepository;
    private final TicketReportUseCase ticketReportUseCase;
    private final UserRepository userRepository;

    public ReportController(
            ReportService reportService,
            TicketRepositoryPort ticketRepository,
            StockBatchRepositoryPort stockBatchRepository,
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
    public ResponseEntity<InputStreamResource> exportTickets(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                LocalDate endDate) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        List<Ticket> tickets = ticketRepository.findAllWithRelations().stream()
                .filter(t -> t.getCreatedAt() != null && (t.getCreatedAt().isEqual(start) || t.getCreatedAt().isAfter(start)) && (t.getCreatedAt().isBefore(end) || t.getCreatedAt().isEqual(end)))
                .toList();

        byte[] excelBytes = reportService.exportTicketsToExcel(tickets);
        ByteArrayInputStream excelFile = new ByteArrayInputStream(excelBytes);

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

        byte[] bytes = ticketReportUseCase.generateTicketReport(userId, user.getRole());
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

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
    public ResponseEntity<InputStreamResource> exportInventoryEntries(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                LocalDate endDate) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        LocalDate startDateLoc = start.toLocalDate();
        LocalDate endDateLoc = end.toLocalDate();

        List<StockBatch> batches = stockBatchRepository.findAll().stream()
                .filter(b -> {
                    if (b.getInstallments() == null || b.getInstallments().isEmpty()) {
                        return b.getEntryDate() != null &&
                               (b.getEntryDate().isEqual(start) || b.getEntryDate().isAfter(start)) &&
                               (b.getEntryDate().isBefore(end) || b.getEntryDate().isEqual(end));
                    } else {
                        return b.getInstallments().stream().anyMatch(inst ->
                                inst.getDueDate() != null &&
                                (inst.getDueDate().isEqual(startDateLoc) || inst.getDueDate().isAfter(startDateLoc)) &&
                                (inst.getDueDate().isBefore(endDateLoc) || inst.getDueDate().isEqual(endDateLoc))
                        );
                    }
                })
                .toList();

        java.util.Map<UUID, BigDecimal> periodCosts = new java.util.HashMap<>();
        for (StockBatch b : batches) {
            if (b.getInstallments() == null || b.getInstallments().isEmpty()) {
                periodCosts.put(b.getId(), b.getUnitPrice().multiply(BigDecimal.valueOf(b.getOriginalQuantity())));
            } else {
                BigDecimal sum = b.getInstallments().stream()
                        .filter(inst -> inst.getDueDate() != null &&
                                        (inst.getDueDate().isEqual(startDateLoc) || inst.getDueDate().isAfter(startDateLoc)) &&
                                        (inst.getDueDate().isBefore(endDateLoc) || inst.getDueDate().isEqual(endDateLoc)))
                        .map(br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatchInstallment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                periodCosts.put(b.getId(), sum);
            }
        }

        byte[] excelBytes = reportService.exportInventoryEntriesToExcel(batches, periodCosts);
        ByteArrayInputStream excelFile = new ByteArrayInputStream(excelBytes);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=entradas_estoque.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }

    /**
     * Gera relatório de saídas de inventário.
     * Suporta formato PDF e Excel.
     */
    @GetMapping("/inventory/exits")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<InputStreamResource> exportInventoryExits(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                LocalDate endDate) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        var format = "xlsx";
        try {
            var req = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (req instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                var fp = sra.getRequest().getParameter("format");
                if (fp != null && !fp.isBlank()) format = fp.toLowerCase();
            }
        } catch (Exception e) {
            // Mantém default
        }

        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdfBytes = reportService.exportInventoryExitsToPdf(start, end);
            ByteArrayInputStream pdfFile = new ByteArrayInputStream(pdfBytes);

            String filename = String.format("saidas_estoque_%s_to_%s.pdf", start.toLocalDate().toString(), end.toLocalDate().toString());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
            headers.setContentType(MediaType.APPLICATION_PDF);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(pdfFile));
        }

        byte[] excelBytes = reportService.exportInventoryExitsToExcel(start, end);
        ByteArrayInputStream excelFile = new ByteArrayInputStream(excelBytes);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=saidas_estoque.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }
}
