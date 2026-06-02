package br.dev.ctrls.inovareti.modules.inventory.domain.port.output;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemCategoryRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemCategory;

public interface ItemCategoryRepositoryPort {
    ItemCategory save(ItemCategory entity);
    Optional<ItemCategory> findById(UUID id);
    List<ItemCategory> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    boolean existsByName(String name);
}
