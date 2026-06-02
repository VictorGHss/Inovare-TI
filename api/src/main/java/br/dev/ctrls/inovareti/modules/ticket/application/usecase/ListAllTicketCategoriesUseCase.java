package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCategoryResponseDTO;
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

    private final TicketCategoryRepositoryPort ticketCategoryRepository;

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
