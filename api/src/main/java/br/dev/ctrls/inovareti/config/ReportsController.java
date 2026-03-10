package br.dev.ctrls.inovareti.config;

import br.dev.ctrls.inovareti.domain.reports.usecase.TicketReportUseCase;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Controlador REST para endpoints de relatórios.
 * Disponibiliza download de relatórios em formato Excel.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final TicketReportUseCase ticketReportUseCase;
    private final UserRepository userRepository;

    /**
     * GET /api/reports/tickets/export
     * Gera e retorna um relatório de chamados em formato Excel.
     * Aplica isolamento por perfil de usuário (tenant isolation).
     *
     * @return ResponseEntity com o fluxo do arquivo Excel
     */
    @GetMapping("/tickets/export")
    public ResponseEntity<Resource> exportTicketsReport() throws IOException {
        log.info("GET /api/reports/tickets/export - Exporting tickets report");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;

        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Could not parse user ID from authentication");
            return ResponseEntity.badRequest().build();
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ByteArrayInputStream stream = ticketReportUseCase.generateTicketReport(userId, user.getRole());

        String filename = "relatorio_chamados_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }
}
