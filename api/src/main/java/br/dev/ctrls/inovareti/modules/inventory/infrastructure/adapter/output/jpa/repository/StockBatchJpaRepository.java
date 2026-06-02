package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;


import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/**
 * Repositório de acesso a dados para a entidade {@link StockBatch}.
 */
@Repository
public interface StockBatchJpaRepository extends JpaRepository<StockBatch, UUID> {

    List<StockBatch> findAllByItem(Item item);

    /**
     * Busca todos os lotes de estoque de um item, ordenados do mais recente para o mais antigo.
     */
    List<StockBatch> findByItemOrderByEntryDateDesc(Item item);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM StockBatch b WHERE b.item.id = :itemId ORDER BY b.entryDate ASC")
    List<StockBatch> findByItemIdOrderByEntryDateAscForUpdate(@Param("itemId") UUID itemId);
}
