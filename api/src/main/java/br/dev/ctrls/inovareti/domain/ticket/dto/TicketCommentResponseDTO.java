package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.ticket.TicketComment;

/**
 * DTO de saída para um comentário de ticket.
 */
public record TicketCommentResponseDTO(
        UUID id,
        String content,
        UUID authorId,
        String authorName,
        LocalDateTime createdAt
) {
    /** Converte uma entidade {@link TicketComment} para este DTO. */
    public static TicketCommentResponseDTO from(TicketComment comment) {
        return new TicketCommentResponseDTO(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getCreatedAt()
        );
    }
}
