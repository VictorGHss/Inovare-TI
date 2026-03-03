package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: lista todos os chamados com suas relações,
 * usando JOIN FETCH para evitar o problema N+1.
 */
@Service
@RequiredArgsConstructor
public class ListAllTicketsUseCase {

    private final TicketRepository ticketRepository;

    /** Retorna todos os chamados mapeados para TicketResponseDTO. */
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> execute() {
        return ticketRepository.findAllWithRelations()
                .stream()
                .map(TicketResponseDTO::from)
                .toList();
    }
}
