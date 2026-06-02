package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketCategory;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCategoryRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCategoryResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: cria uma nova categoria de chamado.
 * Garante unicidade do nome antes de persistir.
 */
@Component
@RequiredArgsConstructor
public class CreateTicketCategoryUseCase {

    private final TicketCategoryRepositoryPort ticketCategoryRepository;

    /**
     * Executa a criação da categoria de chamado.
     *
     * @param request DTO com nome e SLA base
     * @return DTO com os dados da categoria criada
     * @throws ConflictException se já existir uma categoria com o mesmo nome
     */
    @Transactional
    public TicketCategoryResponseDTO execute(TicketCategoryRequestDTO request) {
        if (ticketCategoryRepository.existsByName(request.name())) {
            throw new ConflictException(
                    "Já existe uma categoria de chamado com o nome: " + request.name()
            );
        }

        TicketCategory category = TicketCategory.builder()
                .name(request.name())
                .baseSlaHours(request.baseSlaHours())
                .build();

        return TicketCategoryResponseDTO.from(ticketCategoryRepository.save(category));
    }
}
