package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByItemIdAndTypeOrderByDateDesc(UUID itemId, StockMovementType type);

    List<StockMovement> findByReferenceStartingWithOrderByDateDesc(String referencePrefix);

    // Busca movimentos por prefixo de referência e por tipo (IN/OUT).
    // Usado em relatórios que devem considerar apenas saídas (OUT).
    List<StockMovement> findByReferenceStartingWithAndTypeOrderByDateDesc(String referencePrefix, StockMovementType type);
}
