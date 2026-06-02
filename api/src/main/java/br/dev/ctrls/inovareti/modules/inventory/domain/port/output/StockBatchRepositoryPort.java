package br.dev.ctrls.inovareti.modules.inventory.domain.port.output;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockBatchRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;

public interface StockBatchRepositoryPort {
    StockBatch save(StockBatch entity);
    Optional<StockBatch> findById(UUID id);
    List<StockBatch> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<StockBatch> findByItemIdOrderByEntryDateAscForUpdate(java.util.UUID itemId);
    java.util.List<StockBatch> saveAll(java.util.List<br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch> batches);
    java.util.List<StockBatch> findByItemOrderByEntryDateDesc(br.dev.ctrls.inovareti.modules.inventory.domain.model.Item item);
}
