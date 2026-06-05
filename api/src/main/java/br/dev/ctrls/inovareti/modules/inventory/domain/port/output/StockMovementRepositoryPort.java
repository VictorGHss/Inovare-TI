package br.dev.ctrls.inovareti.modules.inventory.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;



public interface StockMovementRepositoryPort {
    StockMovement save(StockMovement entity);
    Optional<StockMovement> findById(UUID id);
    List<StockMovement> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<StockMovement> findByReferenceStartingWithAndTypeOrderByDateDesc(String reference, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType type);

    java.util.List<StockMovement> findByReferenceStartingWithOrderByDateDesc(String ref);
    java.util.List<StockMovement> findByItemIdAndTypeOrderByDateDesc(java.util.UUID itemId, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType type);

    java.util.List<br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement> findByDateBetweenAndTypeOrderByDateDesc(java.time.LocalDateTime start, java.time.LocalDateTime end, br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType type);
}
