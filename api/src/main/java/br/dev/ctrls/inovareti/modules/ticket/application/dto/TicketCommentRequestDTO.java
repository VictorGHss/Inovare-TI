package br.dev.ctrls.inovareti.modules.ticket.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para requisição de criação de comentário.
 */
public record TicketCommentRequestDTO(
        @NotBlank(message = "O conteúdo do comentário não pode estar vazio")
        String content
) {
}
