package br.dev.ctrls.inovareti.modules.ticket.application.usecase;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;


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
    public List<TicketResponseDTO> execute(UUID userId, UserRole userRole, List<UUID> tagIds) {
        List<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> tickets;
        
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN e TECHNICIAN podem ver todos os chamados
            tickets = ticketRepository.findAllWithRelations();
        } else {
            // USER só pode ver chamados que criou
            tickets = ticketRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        }

        if (tagIds != null && !tagIds.isEmpty()) {
            tickets = tickets.stream()
                    .filter(t -> t.getTags().stream().anyMatch(tag -> tagIds.contains(tag.getId())))
                    .toList();
        }
        
        return tickets.stream()
                .map(TicketResponseDTO::from)
                .toList();
    }
}
