package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Use case: finds a single ticket by its UUID.
 * Throws {@link NotFoundException} when the ticket does not exist.
 */
@Component
@RequiredArgsConstructor
public class FindTicketByIdUseCase {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public TicketResponseDTO execute(UUID id) {
        return ticketRepository.findById(id)
                .map(TicketResponseDTO::from)
                .orElseThrow(() -> new NotFoundException(
                        "Ticket not found with id: " + id));
    }
}
