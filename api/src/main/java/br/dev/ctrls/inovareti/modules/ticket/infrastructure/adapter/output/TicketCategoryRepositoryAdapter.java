package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketCategory;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.TicketCategoryJpaRepository;

@Component
@RequiredArgsConstructor
public class TicketCategoryRepositoryAdapter implements TicketCategoryRepositoryPort {

    private final TicketCategoryJpaRepository repository;

    @Override
    public TicketCategory save(TicketCategory entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<TicketCategory> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<TicketCategory> findAll() {
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
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

}
