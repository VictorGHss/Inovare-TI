package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link Item}.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByNameAndItemCategory(String name, ItemCategory itemCategory);

    /**
     * Busca todos os itens com a categoria carregada via JOIN FETCH,
     * evitando o problema N+1 durante a serialização.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.itemCategory")
    List<Item> findAllWithCategory();

    /**
     * Busca um item por ID com a categoria carregada via JOIN FETCH.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.itemCategory WHERE i.id = :id")
    java.util.Optional<Item> findByIdWithCategory(UUID id);

    /**
     * Counts items with stock at or below the threshold for low stock alerts.
     * @param threshold the maximum stock level to consider as "low stock"
     * @return total number of items with stock <= threshold
     */
    long countByCurrentStockLessThanEqual(int threshold);
}
