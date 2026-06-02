package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;


import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementJpaRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByItemIdAndTypeOrderByDateDesc(UUID itemId, StockMovementType type);

    List<StockMovement> findByReferenceStartingWithOrderByDateDesc(String referencePrefix);

    // Busca movimentos por prefixo de referência e por tipo (IN/OUT).
    // Usado em relatórios que devem considerar apenas saídas (OUT).
    List<StockMovement> findByReferenceStartingWithAndTypeOrderByDateDesc(String referencePrefix, StockMovementType type);

    @org.springframework.data.jpa.repository.Query("SELECT sm FROM StockMovement sm WHERE LOWER(sm.reference) LIKE LOWER(CONCAT('%', :ticketId, '%')) AND sm.type = :type ORDER BY sm.date DESC")
    List<StockMovement> findByReferenceContainingIgnoreCaseAndTypeOrderByDateDesc(@org.springframework.data.repository.query.Param("ticketId") String ticketId, @org.springframework.data.repository.query.Param("type") StockMovementType type);

    List<StockMovement> findByDateBetweenAndTypeOrderByDateDesc(java.time.LocalDateTime start, java.time.LocalDateTime end, StockMovementType type);
}
