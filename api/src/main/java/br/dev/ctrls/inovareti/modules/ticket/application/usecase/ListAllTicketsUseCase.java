package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
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
     * Retorna chamados de acordo com o perfil e ID do usuário.
     *
     * @param userId ID do usuário autenticado
     * @param userRole perfil do usuário autenticado
     * @return lista de chamados visíveis ao usuário
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponseDTO> execute(UUID userId, UserRole userRole, List<UUID> tagIds, org.springframework.data.domain.Pageable pageable) {
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        org.springframework.data.domain.Page<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> ticketsPage;
        
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN e TECHNICIAN podem ver todos os chamados
            ticketsPage = ticketRepository.findAllWithRelations(hasTags, tagIds, pageable);
        } else {
            // USER só pode ver chamados que criou
            ticketsPage = ticketRepository.findByRequesterIdWithRelations(userId, hasTags, tagIds, pageable);
        }
        
        return ticketsPage.map(TicketResponseDTO::from);
    }
}
