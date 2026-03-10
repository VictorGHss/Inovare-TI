package br.dev.ctrls.inovareti.domain.reports.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para exportação de relatório de chamados.
 * Contém todas as informações necessárias para a exportação em Excel.
 */
public record TicketReportDTO(
    UUID id,
    String title,
    String requesterName,
    String sectorName,
    String status,
    LocalDateTime createdAt,
    LocalDateTime resolvedAt
) {}
