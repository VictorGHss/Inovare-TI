package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: vincula um usuário adicional afetado a um chamado existente.
 *
 * <p>O usuário adicionado é inserido na tabela {@code ticket_additional_users}.
 * Ao fechar o chamado, o {@link ResolveTicketUseCase} percorrerá essa lista e
 * enviará uma DM no Discord para cada usuário adicional afetado.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AddAdditionalUserUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId, UUID userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Chamado não encontrado: " + ticketId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));

        ticket.getAdditionalUsers().add(user);
        Ticket saved = ticketRepository.save(ticket);

        log.info("[TICKET] Usuário adicional '{}' ({}) vinculado ao chamado {}",
                user.getName(), userId, ticketId);

        return TicketResponseDTO.from(saved);
    }
}
