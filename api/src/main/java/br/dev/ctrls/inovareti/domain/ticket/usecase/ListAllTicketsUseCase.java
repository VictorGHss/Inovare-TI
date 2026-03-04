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
 * Use case: lists all tickets with tenant isolation based on user role.
 * 
 * - ADMIN/TECHNICIAN: can see all tickets
 * - USER: can only see their own tickets (where they are the requester)
 */
@Service
@RequiredArgsConstructor
public class ListAllTicketsUseCase {

    private final TicketRepository ticketRepository;

    /**
     * Returns tickets based on user role and ID.
     * 
     * @param userId the authenticated user's ID
     * @param userRole the authenticated user's role
     * @return list of tickets the user can see
     */
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> execute(UUID userId, UserRole userRole) {
        List<TicketResponseDTO> tickets;
        
        if (userRole == UserRole.ADMIN || userRole == UserRole.TECHNICIAN) {
            // ADMIN and TECHNICIAN can see all tickets
            tickets = ticketRepository.findAllWithRelations()
                    .stream()
                    .map(TicketResponseDTO::from)
                    .toList();
        } else {
            // USER can only see tickets they created
            tickets = ticketRepository.findByRequesterIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .map(TicketResponseDTO::from)
                    .toList();
        }
        
        return tickets;
    }
}
