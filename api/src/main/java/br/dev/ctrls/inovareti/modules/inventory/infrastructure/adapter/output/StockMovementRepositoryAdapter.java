package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.StockMovementJpaRepository;

@Component
@RequiredArgsConstructor
public class StockMovementRepositoryAdapter implements StockMovementRepositoryPort {

    private final StockMovementJpaRepository repository;

    @Override
    public StockMovement save(StockMovement entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<StockMovement> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<StockMovement> findAll() {
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
    public java.util.List<StockMovement> findByReferenceStartingWithAndTypeOrderByDateDesc(String reference, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType type) {
        return repository.findByReferenceStartingWithAndTypeOrderByDateDesc(reference, type);
    }


    @Override
    public java.util.List<StockMovement> findByReferenceStartingWithOrderByDateDesc(String ref) {
        return repository.findByReferenceStartingWithOrderByDateDesc(ref);
    }

    @Override
    public java.util.List<StockMovement> findByItemIdAndTypeOrderByDateDesc(java.util.UUID itemId, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType type) {
        return repository.findByItemIdAndTypeOrderByDateDesc(itemId, type);
    }


    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement> findByDateBetweenAndTypeOrderByDateDesc(java.time.LocalDateTime start, java.time.LocalDateTime end, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType type) {
        return repository.findByDateBetweenAndTypeOrderByDateDesc(start, end, type);
    }

}
