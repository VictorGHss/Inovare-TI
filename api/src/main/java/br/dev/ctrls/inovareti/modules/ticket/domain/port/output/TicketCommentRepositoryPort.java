package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCommentRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketComment;

public interface TicketCommentRepositoryPort {
    TicketComment save(TicketComment entity);
    Optional<TicketComment> findById(UUID id);
    List<TicketComment> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<TicketComment> findByTicketIdWithAuthor(java.util.UUID ticketId);
}
