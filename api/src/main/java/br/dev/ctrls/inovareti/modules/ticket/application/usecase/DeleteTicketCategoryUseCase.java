package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
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

    private final TicketCategoryRepositoryPort ticketCategoryRepository;
    private final TicketRepositoryPort ticketRepository;

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
