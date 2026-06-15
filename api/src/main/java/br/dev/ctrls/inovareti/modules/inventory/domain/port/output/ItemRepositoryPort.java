package br.dev.ctrls.inovareti.modules.inventory.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;



public interface ItemRepositoryPort {
    Item save(Item entity);
    Optional<Item> findById(UUID id);
    List<Item> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.Optional<Item> findByIdForUpdate(java.util.UUID id);
    java.util.Optional<Item> findByIdWithCategory(java.util.UUID id);
    org.springframework.data.domain.Page<Item> findAllOrderByOldestBatchEntryDateAsc(boolean inStock, int threshold, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Item> findAllOrderByOldestBatchEntryDateDesc(boolean inStock, int threshold, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Item> findByCurrentStockLessThanEqual(int stock, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Item> findAll(org.springframework.data.domain.Pageable pageable);
    long countByItemCategory_Id(java.util.UUID id);

    java.util.List<br.dev.ctrls.inovareti.modules.inventory.domain.model.Item> findTop25ByNameContainingIgnoreCase(String name);

    org.springframework.data.domain.Page<Item> findByNameContainingIgnoreCase(String name, org.springframework.data.domain.Pageable pageable);

    long count();
    long countByCurrentStockLessThanEqual(int stock);

    org.springframework.data.domain.Page<Item> findObsoleteItems(org.springframework.data.domain.Pageable pageable);
}
