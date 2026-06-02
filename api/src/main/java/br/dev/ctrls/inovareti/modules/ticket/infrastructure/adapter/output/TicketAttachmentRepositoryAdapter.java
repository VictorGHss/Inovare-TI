package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketAttachment;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.TicketAttachmentJpaRepository;

@Component
@RequiredArgsConstructor
public class TicketAttachmentRepositoryAdapter implements TicketAttachmentRepositoryPort {

    private final TicketAttachmentJpaRepository repository;

    @Override
    public TicketAttachment save(TicketAttachment entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<TicketAttachment> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<TicketAttachment> findAll() {
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
    public java.util.List<TicketAttachment> findByTicketId(java.util.UUID ticketId) {
        return repository.findByTicketId(ticketId);
    }

    @Override
    public java.util.Optional<TicketAttachment> findByStoredFilename(String storedFilename) {
        return repository.findByStoredFilename(storedFilename);
    }

}
