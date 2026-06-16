package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemAllocationEntity;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemAllocationRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.ItemAllocationJpaRepository;

@Component
@RequiredArgsConstructor
public class ItemAllocationRepositoryAdapter implements ItemAllocationRepositoryPort {

    private final ItemAllocationJpaRepository repository;

    @Override
    public ItemAllocationEntity save(ItemAllocationEntity entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<ItemAllocationEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<ItemAllocationEntity> findAll() {
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
    public List<ItemAllocationEntity> findByChildItemIdOrderByAllocatedAtDesc(UUID childItemId) {
        return repository.findByChildItemIdOrderByAllocatedAtDesc(childItemId);
    }

    @Override
    public List<ItemAllocationEntity> findByParentItemIdOrderByAllocatedAtDesc(UUID parentItemId) {
        return repository.findByParentItemIdOrderByAllocatedAtDesc(parentItemId);
    }
}
