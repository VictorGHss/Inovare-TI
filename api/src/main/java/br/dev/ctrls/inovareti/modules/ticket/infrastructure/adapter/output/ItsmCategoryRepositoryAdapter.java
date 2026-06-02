package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.ItsmCategory;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.ItsmCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.ItsmCategoryJpaRepository;

@Component
@RequiredArgsConstructor
public class ItsmCategoryRepositoryAdapter implements ItsmCategoryRepositoryPort {

    private final ItsmCategoryJpaRepository repository;

    @Override
    public ItsmCategory save(ItsmCategory entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<ItsmCategory> findById(Integer id) {
        return repository.findById(id);
    }

    @Override
    public List<ItsmCategory> findAll() {
        return repository.findAll();
    }

    @Override
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }
    
    @Override
    public boolean existsById(Integer id) {
        return repository.existsById(id);
    }
}
