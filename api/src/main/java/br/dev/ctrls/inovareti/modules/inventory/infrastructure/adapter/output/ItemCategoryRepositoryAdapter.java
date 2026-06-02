package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemCategory;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.ItemCategoryJpaRepository;

@Component
@RequiredArgsConstructor
public class ItemCategoryRepositoryAdapter implements ItemCategoryRepositoryPort {

    private final ItemCategoryJpaRepository repository;

    @Override
    public ItemCategory save(ItemCategory entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<ItemCategory> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<ItemCategory> findAll() {
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
