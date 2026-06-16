package br.dev.ctrls.inovareti.modules.inventory.domain.port.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemAllocationEntity;

public interface ItemAllocationRepositoryPort {
    ItemAllocationEntity save(ItemAllocationEntity entity);
    Optional<ItemAllocationEntity> findById(UUID id);
    List<ItemAllocationEntity> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    List<ItemAllocationEntity> findByChildItemIdOrderByAllocatedAtDesc(UUID childItemId);
    List<ItemAllocationEntity> findByParentItemIdOrderByAllocatedAtDesc(UUID parentItemId);
}
