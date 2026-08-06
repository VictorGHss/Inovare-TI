package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: busca chamados resolvidos/fechados que compartilham tags com o chamado especificado.
 * Executado dentro de escopo transacional de leitura (@Transactional(readOnly = true))
 * para prevenir LazyInitializationException ao acessar a coleção de tags da entidade.
 */
@Component
@RequiredArgsConstructor
public class FindSimilarTicketsUseCase {

    private final TicketRepositoryPort ticketRepository;

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> execute(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + id));

        if (ticket.getTags() == null || ticket.getTags().isEmpty()) {
            return List.of();
        }

        return ticketRepository.findSimilarResolvedTickets(id, ticket.getTags())
                .stream()
                .map(TicketResponseDTO::from)
                .toList();
    }
}
