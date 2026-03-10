package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para resposta de anexo de chamado.
 * @param id UUID do anexo
 * @param originalFilename nome original do arquivo no upload
 * @param storedFilename nome do arquivo baseado em UUID armazenado no servidor
 * @param fileType tipo MIME do arquivo
 * @param ticketId UUID do chamado associado
 * @param uploadedAt data e hora do upload
 */
public record TicketAttachmentResponseDTO(
    UUID id,
    String originalFilename,
    String storedFilename,
    String fileType,
    UUID ticketId,
    LocalDateTime uploadedAt
) {}
