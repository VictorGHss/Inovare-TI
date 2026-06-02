package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockBatchRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.StockBatchJpaRepository;

@Component
@RequiredArgsConstructor
public class StockBatchRepositoryAdapter implements StockBatchRepositoryPort {

    private final StockBatchJpaRepository repository;

    @Override
    public StockBatch save(StockBatch entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<StockBatch> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<StockBatch> findAll() {
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
    public java.util.List<StockBatch> findByItemIdOrderByEntryDateAscForUpdate(java.util.UUID itemId) {
        return repository.findByItemIdOrderByEntryDateAscForUpdate(itemId);
    }

    @Override
    public java.util.List<StockBatch> saveAll(java.util.List<br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch> batches) {
        return repository.saveAll(batches);
    }

    @Override
    public java.util.List<StockBatch> findByItemOrderByEntryDateDesc(br.dev.ctrls.inovareti.modules.inventory.domain.model.Item item) {
        return repository.findByItemOrderByEntryDateDesc(item);
    }

}
