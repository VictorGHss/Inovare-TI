package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCommentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetTicketCommentsUseCase {

    private final TicketCommentRepositoryPort commentRepository;
    private final TicketRepositoryPort ticketRepository;

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDTO> execute(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new NotFoundException("Chamado não encontrado com id: " + ticketId);
        }

        var comments = commentRepository.findByTicketIdWithAuthor(ticketId)
                .stream()
                .map(TicketCommentResponseDTO::from)
                .toList();

        log.info("Retrieved {} comments for ticket {}", comments.size(), ticketId);

        return comments;
    }
}
