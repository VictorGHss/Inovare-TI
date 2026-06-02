package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketComment;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCommentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddTicketCommentUseCase {

    private final TicketCommentRepositoryPort commentRepository;
    private final TicketRepositoryPort ticketRepository;
    private final UserRepository userRepository;
    private final CreateNotificationService notificationService;

    @Transactional
    public TicketCommentResponseDTO execute(UUID ticketId, TicketCommentRequestDTO request) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        User author = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado com id: " + userId));

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + ticketId));

        TicketComment comment = TicketComment.builder()
                .content(request.content())
                .ticket(ticket)
                .author(author)
                .createdAt(LocalDateTime.now())
                .build();

        TicketComment savedComment = commentRepository.save(comment);
// Dispara notificações dependendo de quem comentou
        if (author.getId().equals(ticket.getRequester().getId())) {
            // Solicitante comentou: notifica o técnico responsável
            if (ticket.getAssignedTo() != null) {
                notificationService.create(
                    ticket.getAssignedTo().getId(),
                    "Novo comentário no chamado",
                    String.format("O solicitante comentou no chamado #%s", ticket.getId().toString().substring(0, 8)),
                    String.format("/tickets/%s", ticket.getId())
                );
            }
        } else {
            // Técnico/Admin comentou: notifica o solicitante
            notificationService.create(
                ticket.getRequester().getId(),
                "Novo comentário no chamado",
                String.format("Há uma resposta no chamado #%s", ticket.getId().toString().substring(0, 8)),
                String.format("/tickets/%s", ticket.getId())
            );
        }

        
        log.info("Comentário adicionado ao chamado {} pelo usuário {} ({})",
            ticketId, author.getName(), author.getEmail());

        return TicketCommentResponseDTO.from(savedComment);
    }
}
