package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

import br.dev.ctrls.inovareti.domain.financeiro.FinancialTransaction;
import br.dev.ctrls.inovareti.domain.financeiro.FinancialTransactionRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockBatch;
import br.dev.ctrls.inovareti.domain.inventory.StockBatchRepository;
import br.dev.ctrls.inovareti.domain.reports.usecase.TicketReportUseCase;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final TicketRepository ticketRepository;
    private final StockBatchRepository stockBatchRepository;
    private final TicketReportUseCase ticketReportUseCase;
    private final UserRepository userRepository;
    private final FinancialTransactionRepository transactionRepository;

    public ReportController(
            ReportService reportService,
            TicketRepository ticketRepository,
            StockBatchRepository stockBatchRepository,
            TicketReportUseCase ticketReportUseCase,
            UserRepository userRepository,
            FinancialTransactionRepository transactionRepository) {
        this.reportService = reportService;
        this.ticketRepository = ticketRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.ticketReportUseCase = ticketReportUseCase;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
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
        public ResponseEntity<InputStreamResource> exportInventoryEntries(
                @org.springframework.web.bind.annotation.RequestParam(required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
                @org.springframework.web.bind.annotation.RequestParam(required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        List<StockBatch> batches = stockBatchRepository.findAll().stream()
            .filter(b -> b.getEntryDate() != null && (b.getEntryDate().isEqual(start) || b.getEntryDate().isAfter(start)) && (b.getEntryDate().isBefore(end) || b.getEntryDate().isEqual(end)))
            .toList();

        ByteArrayInputStream excelFile = reportService.exportInventoryEntriesToExcel(batches);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=entradas_estoque.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }

    /**
     * Gera relatório de saídas de inventário.
     * Inclui chamados fechados no período informado (o filtro final é inclusivo
     * até 23:59:59 no fuso UTC) e também inclui chamados que aparecem em
     * lançamentos da tabela `financial_transactions` onde
     * `resource_type = INVENTORY` e `target_type` é SECTOR ou DOCTOR.
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

        // Define intervalo solicitado (data/hora) e garante que o filtro final inclua o dia inteiro
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        // Busca lançamentos financeiros no intervalo (interpretação em UTC no banco).
        // Filtra apenas resource_type = INVENTORY e target_type em (SECTOR, DOCTOR).
        List<FinancialTransaction> txs = transactionRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
            .filter(tx -> tx.getResourceType() == FinancialTransaction.ResourceType.INVENTORY
                && (tx.getTargetType() == FinancialTransaction.TargetType.SECTOR || tx.getTargetType() == FinancialTransaction.TargetType.DOCTOR))
            .collect(Collectors.toList());

        // Coleta ticket_ids presentes nos lançamentos financeiros (se houver)
        Set<UUID> ticketIds = txs.stream().map(FinancialTransaction::getTicketId).filter(Objects::nonNull).collect(Collectors.toSet());

        // Reúne chamados para o relatório: ou fechados no intervalo solicitado OU referenciados em financial_transactions
        List<Ticket> tickets = ticketRepository.findAllWithRelations().stream()
            .filter(t -> (t.getClosedAt() != null && (t.getClosedAt().isEqual(start) || t.getClosedAt().isAfter(start)) && (t.getClosedAt().isBefore(end) || t.getClosedAt().isEqual(end)))
                || ticketIds.contains(t.getId()))
            .toList();

        // Suporta ?format=pdf para retornar PDF em vez de Excel
        var format = "xlsx"; // padrão
        try {
            var req = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (req instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                var fp = sra.getRequest().getParameter("format");
                if (fp != null && !fp.isBlank()) format = fp.toLowerCase();
            }
        } catch (Exception e) {
            // swallow - keep default
        }

        if ("pdf".equalsIgnoreCase(format)) {
            ByteArrayInputStream pdfFile = reportService.exportInventoryExitsToPdf(tickets);

            String filename = String.format("saidas_estoque_%s_to_%s.pdf", start.toLocalDate().toString(), end.toLocalDate().toString());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
            headers.setContentType(MediaType.APPLICATION_PDF);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(pdfFile));
        }

        ByteArrayInputStream excelFile = reportService.exportInventoryExitsToExcel(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=saidas_estoque.xlsx");
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(excelFile));
    }
}
