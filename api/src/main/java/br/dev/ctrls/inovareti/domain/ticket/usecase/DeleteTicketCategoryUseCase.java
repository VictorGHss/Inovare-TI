package br.dev.ctrls.inovareti.domain.ticket.usecase;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso: exclui uma categoria de chamado.
 * Impede a exclusão quando houver tickets vinculados à categoria.
 */
@Component
@RequiredArgsConstructor
public class DeleteTicketCategoryUseCase {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;

    /**
     * Executa a exclusão da categoria de chamado.
     *
     * @param id UUID da categoria a excluir
     * @throws NotFoundException se a categoria não existir
     * @throws ConflictException se houver tickets vinculados à categoria
     */
    @Transactional
    public void execute(UUID id) {
        if (!ticketCategoryRepository.existsById(id)) {
            throw new NotFoundException("Categoria de chamado não encontrada: " + id);
        }

        if (ticketRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Não é possível excluir a categoria pois há chamados vinculados a ela."
            );
        }

        ticketCategoryRepository.deleteById(id);
    }
}
