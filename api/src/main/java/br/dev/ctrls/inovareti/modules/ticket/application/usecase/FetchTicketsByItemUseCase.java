package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.service.TicketSpecification;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso encarregado de buscar, de forma paginada, todos os chamados
 * (incidentes ou solicitações de itens) associados a um item de inventário específico.
 */
@Service
@RequiredArgsConstructor
public class FetchTicketsByItemUseCase {

    private final TicketRepositoryPort ticketRepository;

    /**
     * Recupera a página de chamados associados ao item do inventário informado.
     *
     * @param itemId O identificador único do item de inventário (UUID)
     * @param pageable Configurações de paginação (página, quantidade e ordenação)
     * @return Página de DTOs contendo as informações simplificadas dos chamados
     */
    @Transactional(readOnly = true)
    public Page<TicketResponseDTO> execute(UUID itemId, Pageable pageable) {
        // Cria a especificação para filtrar chamados vinculados ao itemId
        var spec = TicketSpecification.byItemId(itemId);
        
        // Retorna a página mapeada para DTOs de resposta
        return ticketRepository.findAll(spec, pageable).map(TicketResponseDTO::from);
    }
}
