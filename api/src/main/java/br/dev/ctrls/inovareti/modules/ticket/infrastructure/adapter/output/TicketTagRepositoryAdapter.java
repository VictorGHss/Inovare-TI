package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketTagRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.TicketTagJpaRepository;

@Component
@RequiredArgsConstructor
public class TicketTagRepositoryAdapter implements TicketTagRepositoryPort {

    private final TicketTagJpaRepository repository;

    @Override
    public TicketTag save(TicketTag entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<TicketTag> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<TicketTag> findAll() {
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
    public java.util.List<TicketTag> findAllByActiveTrue() {
        return repository.findAllByActiveTrue();
    }

    @Override
    public java.util.Optional<TicketTag> findByNameIgnoreCase(String name) {
        return repository.findByNameIgnoreCase(name);
    }

    @Override
    public void delete(TicketTag tag) {
        repository.delete(tag);
    }

}
