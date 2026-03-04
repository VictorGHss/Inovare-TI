package br.dev.ctrls.inovareti.domain.reports.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for ticket report export.
 * Contains all information needed for Excel export.
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
