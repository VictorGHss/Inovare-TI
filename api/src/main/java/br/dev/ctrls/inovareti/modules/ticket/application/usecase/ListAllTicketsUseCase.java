package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: lista todos os chamados com isolamento por perfil de usuário.
 *
 * - ADMIN/TECHNICIAN: podem ver todos os chamados
 * - USER: só podem ver seus próprios chamados (onde são o solicitante)
 */
@Service
@RequiredArgsConstructor
public class ListAllTicketsUseCase {

    private final TicketRepositoryPort ticketRepository;

    /**
     * Retorna chamados de acordo com o perfil, ID do utilizador, filtros dinâmicos e paginação.
     *
     * @param userId ID do utilizador autenticado
     * @param userRole perfil do utilizador autenticado
     * @param tagIds lista de tags para filtragem
     * @param search termo opcional para pesquisa global
     * @param status status opcional para filtragem no servidor
     * @param priority prioridade opcional para filtragem no servidor
     * @param categoryId identificador opcional da categoria para filtragem
     * @param pageable paginação
     * @return lista de chamados visíveis ao utilizador com os filtros aplicados
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponseDTO> execute(
            UUID userId,
            UserRole userRole,
            List<UUID> tagIds,
            String search,
            TicketStatus status,
            TicketPriority priority,
            UUID categoryId,
            org.springframework.data.domain.Pageable pageable) {
        
        // Aplica isolamento de visibilidade por perfil
        UUID restrictionId = (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) ? null : userId;
        
        // Cria especificação unificada para busca paginada com ordenação
        org.springframework.data.jpa.domain.Specification<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> spec = 
            br.dev.ctrls.inovareti.modules.ticket.application.service.TicketSpecification.filterTickets(
                restrictionId,
                tagIds,
                search,
                status,
                priority,
                categoryId
            );
        
        return ticketRepository.findAll(spec, pageable).map(TicketResponseDTO::from);
    }
}
