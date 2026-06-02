package br.dev.ctrls.inovareti.modules.report.application.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.report.application.dto.TicketReportDTO;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportExcelExporterPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: gera os dados para o relatório de chamados e delega a montagem física ao exportador.
 * Aplica isolamento por perfil (USER vê apenas seus chamados, ADMIN vê todos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReportUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final ReportExcelExporterPort reportExcelExporter;

    /**
     * Gera os dados do relatório e retorna o byte[] gerado física e transparentemente.
     *
     * @param userId o ID do usuário autenticado
     * @param userRole o perfil do usuário autenticado
     * @return byte[] contendo o arquivo Excel
     */
    @Transactional(readOnly = true)
    public byte[] generateTicketReport(UUID userId, UserRole userRole) {
        log.info("Gerando dados de relatório de chamados para usuário: {} (perfil: {})", userId, userRole);

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
