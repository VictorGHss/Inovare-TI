package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketComment;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCommentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.TicketCommentJpaRepository;

@Component
@RequiredArgsConstructor
public class TicketCommentRepositoryAdapter implements TicketCommentRepositoryPort {

    private final TicketCommentJpaRepository repository;

    @Override
    public TicketComment save(TicketComment entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<TicketComment> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<TicketComment> findAll() {
        return repository.findAll();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
    
    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public java.util.List<TicketComment> findByTicketIdWithAuthor(java.util.UUID ticketId) {
        return repository.findByTicketIdWithAuthor(ticketId);
    }

}
