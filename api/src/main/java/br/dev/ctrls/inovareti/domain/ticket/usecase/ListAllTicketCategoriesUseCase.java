package br.dev.ctrls.inovareti.domain.ticket.usecase;

import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCategoryResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: lista todas as categorias de chamado cadastradas.
 */
@Component
@RequiredArgsConstructor
public class ListAllTicketCategoriesUseCase {

    private final TicketCategoryRepository ticketCategoryRepository;

    /**
     * Retorna todas as categorias de chamado.
     *
     * @return lista de DTOs com os dados das categorias
     */
    @Transactional(readOnly = true)
    public List<TicketCategoryResponseDTO> execute() {
        return ticketCategoryRepository.findAll()
                .stream()
                .map(TicketCategoryResponseDTO::from)
                .toList();
    }
}
