package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.user.UserRole;
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

    private final TicketRepository ticketRepository;

    /**
     * Retorna chamados de acordo com o perfil e ID do usuário.
     *
     * @param userId ID do usuário autenticado
     * @param userRole perfil do usuário autenticado
     * @return lista de chamados visíveis ao usuário
     */
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> execute(UUID userId, UserRole userRole) {
        List<TicketResponseDTO> tickets;
        
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN e TECHNICIAN podem ver todos os chamados
            tickets = ticketRepository.findAllWithRelations()
                    .stream()
                    .map(TicketResponseDTO::from)
                    .toList();
        } else {
            // USER só pode ver chamados que criou
            tickets = ticketRepository.findByRequesterIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .map(TicketResponseDTO::from)
                    .toList();
        }
        
        return tickets;
    }
}
