package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.ticket.TicketCommentRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCommentResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetTicketCommentsUseCase {

    private final TicketCommentRepository commentRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDTO> execute(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new NotFoundException("Ticket not found with id: " + ticketId);
        }

        var comments = commentRepository.findByTicketIdWithAuthor(ticketId)
                .stream()
                .map(TicketCommentResponseDTO::from)
                .toList();

        log.info("Retrieved {} comments for ticket {}", comments.size(), ticketId);

        return comments;
    }
}
