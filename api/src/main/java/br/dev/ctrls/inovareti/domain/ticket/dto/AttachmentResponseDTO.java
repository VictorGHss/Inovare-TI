package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

/**
 * DTO para anexo na resposta do chamado.
 * @param id UUID do anexo
 * @param originalFilename nome original do arquivo no momento do upload
 * @param fileUrl URL para acessar o arquivo
 * @param fileType tipo MIME do arquivo
 */
public record AttachmentResponseDTO(
    UUID id,
    String originalFilename,
    String fileUrl,
    String fileType
) {}
