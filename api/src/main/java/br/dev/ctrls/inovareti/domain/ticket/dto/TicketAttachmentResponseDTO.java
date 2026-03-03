package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for ticket attachment response.
 * @param id attachment UUID
 * @param originalFilename the original filename when uploaded
 * @param storedFilename the UUID-based filename on server
 * @param fileType MIME type of the file
 * @param ticketId UUID of the associated ticket
 * @param uploadedAt timestamp when the file was uploaded
 */
public record TicketAttachmentResponseDTO(
    UUID id,
    String originalFilename,
    String storedFilename,
    String fileType,
    UUID ticketId,
    LocalDateTime uploadedAt
) {}
