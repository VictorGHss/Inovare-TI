package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link StockBatch}.
 */
@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

    List<StockBatch> findAllByItem(Item item);
}
