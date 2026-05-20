package br.dev.ctrls.inovareti.domain.inventory;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/**
 * Repositório de acesso a dados para a entidade {@link Item}.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByNameAndItemCategory(String name, ItemCategory itemCategory);

    @Override
    @EntityGraph(attributePaths = "itemCategory")
    Page<Item> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "itemCategory")
    Page<Item> findByCurrentStockLessThanEqual(int threshold, Pageable pageable);

    // No Postgres, todos os campos projetados de Item e ItemCategory precisam estar no GROUP BY.
    @Query(value = """
            SELECT i
            FROM Item i
            JOIN FETCH i.itemCategory ic
            LEFT JOIN StockBatch sb ON sb.item = i AND sb.remainingQuantity > 0
            WHERE (:lowStockOnly = false OR i.currentStock <= :threshold)
            GROUP BY i.id, i.name, i.currentStock, i.specifications,
                     ic.id, ic.name, ic.isConsumable
            ORDER BY
            CASE WHEN MIN(sb.entryDate) IS NULL THEN 1 ELSE 0 END,
            MIN(sb.entryDate) ASC,
            i.name ASC
            """,
            countQuery = """
            SELECT COUNT(i)
            FROM Item i
            WHERE (:lowStockOnly = false OR i.currentStock <= :threshold)
            """)
    Page<Item> findAllOrderByOldestBatchEntryDateAsc(
            @Param("lowStockOnly") boolean lowStockOnly,
            @Param("threshold") int threshold,
            Pageable pageable);

    // Mantém o mesmo critério de GROUP BY completo para a variante de ordenação descendente.
    @Query(value = """
            SELECT i
            FROM Item i
            JOIN FETCH i.itemCategory ic
            LEFT JOIN StockBatch sb ON sb.item = i AND sb.remainingQuantity > 0
            WHERE (:lowStockOnly = false OR i.currentStock <= :threshold)
            GROUP BY i.id, i.name, i.currentStock, i.specifications,
                     ic.id, ic.name, ic.isConsumable
            ORDER BY
            CASE WHEN MIN(sb.entryDate) IS NULL THEN 1 ELSE 0 END,
            MIN(sb.entryDate) DESC,
            i.name ASC
            """,
            countQuery = """
            SELECT COUNT(i)
            FROM Item i
            WHERE (:lowStockOnly = false OR i.currentStock <= :threshold)
            """)
    Page<Item> findAllOrderByOldestBatchEntryDateDesc(
            @Param("lowStockOnly") boolean lowStockOnly,
            @Param("threshold") int threshold,
            Pageable pageable);

    /**
     * Busca um item por ID com a categoria carregada via JOIN FETCH.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.itemCategory WHERE i.id = :id")
    java.util.Optional<Item> findByIdWithCategory(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    java.util.Optional<Item> findByIdForUpdate(UUID id);

    // Conta quantos itens estão vinculados a uma categoria específica
    long countByItemCategory_Id(UUID categoryId);

    /**
     * Conta itens com estoque igual ou inferior ao limiar de alerta de estoque baixo.
     * @param threshold o nível máximo de estoque para considerar como "estoque baixo"
     * @return total de itens com estoque <= threshold
     */
    long countByCurrentStockLessThanEqual(int threshold);

    /**
     * Busca até 25 itens cujo nome contenha o texto digitado (case-insensitive).
     * Utilizado pelo autocomplete do comando /solicitar no Discord.
     * Limitado a 25 pois a API do Discord não aceita mais que 25 opções de autocomplete.
     */
    java.util.List<Item> findTop25ByNameContainingIgnoreCase(String nome);
}
