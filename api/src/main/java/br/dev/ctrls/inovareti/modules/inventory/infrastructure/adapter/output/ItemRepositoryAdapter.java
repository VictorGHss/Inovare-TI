package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.ItemJpaRepository;

@Component
@RequiredArgsConstructor
public class ItemRepositoryAdapter implements ItemRepositoryPort {

    private final ItemJpaRepository repository;

    @Override
    public Item save(Item entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<Item> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Item> findAll() {
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
    public java.util.Optional<Item> findByIdForUpdate(java.util.UUID id) {
        return repository.findByIdForUpdate(id);
    }

    @Override
    public java.util.Optional<Item> findByIdWithCategory(java.util.UUID id) {
        return repository.findByIdWithCategory(id);
    }

    @Override
    public org.springframework.data.domain.Page<Item> findAllOrderByOldestBatchEntryDateAsc(boolean inStock, int threshold, org.springframework.data.domain.Pageable pageable) {
        return repository.findAllOrderByOldestBatchEntryDateAsc(inStock, threshold, pageable);
    }

    @Override
    public org.springframework.data.domain.Page<Item> findAllOrderByOldestBatchEntryDateDesc(boolean inStock, int threshold, org.springframework.data.domain.Pageable pageable) {
        return repository.findAllOrderByOldestBatchEntryDateDesc(inStock, threshold, pageable);
    }

    @Override
    public org.springframework.data.domain.Page<Item> findByCurrentStockLessThanEqual(int stock, org.springframework.data.domain.Pageable pageable) {
        return repository.findByCurrentStockLessThanEqual(stock, pageable);
    }

    @Override
    public org.springframework.data.domain.Page<Item> findAll(org.springframework.data.domain.Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public long countByItemCategory_Id(java.util.UUID id) {
        return repository.countByItemCategory_Id(id);
    }


    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.inventory.domain.model.Item> findTop25ByNameContainingIgnoreCase(String name) {
        return repository.findTop25ByNameContainingIgnoreCase(name);
    }


    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public long countByCurrentStockLessThanEqual(int stock) {
        return repository.countByCurrentStockLessThanEqual(stock);
    }

}
