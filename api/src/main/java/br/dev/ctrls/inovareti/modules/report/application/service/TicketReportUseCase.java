package br.dev.ctrls.inovareti.modules.report.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.report.application.dto.TicketReportDTO;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportExcelExporterPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: gera os dados para o relatÃ³rio de chamados e delega a montagem fÃ­sica ao exportador.
 * Aplica isolamento por perfil (USER vÃª apenas seus chamados, ADMIN vÃª todos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class TicketReportUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final ReportExcelExporterPort reportExcelExporter;

    /**
     * Gera os dados do relatÃ³rio e retorna o byte[] gerado fÃ­sica e transparentemente.
     *
     * @param userId o ID do usuÃ¡rio autenticado
     * @param userRole o perfil do usuÃ¡rio autenticado
     * @return byte[] contendo o arquivo Excel
     */
    @Transactional(readOnly = true)
    public byte[] generateTicketReport(UUID userId, UserRole userRole) {
        log.info("Gerando dados de relatÃ³rio de chamados para usuÃ¡rio: {} (perfil: {})", userId, userRole);

        List<Ticket> tickets;
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            tickets = ticketRepository.findAll();
        } else {
            tickets = ticketRepository.findByRequesterId(userId);
        }

        List<TicketReportDTO> reportData = tickets.stream()
                .map(this::mapTicketToReport)
                .collect(Collectors.toList());

        return reportExcelExporter.generateTicketReport(reportData);
    }

    /**
     * Converte uma entidade Ticket em TicketReportDTO.
     */
    private TicketReportDTO mapTicketToReport(Ticket ticket) {
        return new TicketReportDTO(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getRequester() != null ? ticket.getRequester().getName() : "N/A",
                ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "N/A",
                ticket.getStatus() != null ? ticket.getStatus().toString() : "N/A",
                ticket.getCreatedAt(),
                ticket.getClosedAt()
        );
    }
}


