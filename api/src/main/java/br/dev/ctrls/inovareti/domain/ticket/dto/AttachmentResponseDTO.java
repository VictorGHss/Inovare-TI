package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

/**
 * DTO for attachment in ticket response.
 * @param id attachment UUID
 * @param originalFilename the original filename when uploaded
 * @param fileUrl URL to access the file
 * @param fileType MIME type of the file
 */
public record AttachmentResponseDTO(
    UUID id,
    String originalFilename,
    String fileUrl,
    String fileType
) {}
