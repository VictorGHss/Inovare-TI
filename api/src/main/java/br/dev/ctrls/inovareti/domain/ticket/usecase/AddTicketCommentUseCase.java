package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketComment;
import br.dev.ctrls.inovareti.domain.ticket.TicketCommentRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCommentRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCommentResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddTicketCommentUseCase {

    private final TicketCommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CreateNotificationService notificationService;

    @Transactional
    public TicketCommentResponseDTO execute(UUID ticketId, TicketCommentRequestDTO request) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Authenticated user not found with id: " + userId));

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));

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

        
        log.info("Comment added to ticket {} by user {} ({})",
                ticketId, author.getName(), author.getEmail());

        return TicketCommentResponseDTO.from(savedComment);
    }
}
