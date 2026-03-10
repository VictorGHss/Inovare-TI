package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    java.util.Optional<Item> findByIdForUpdate(UUID id);

    /**
     * Conta itens com estoque igual ou inferior ao limiar de alerta de estoque baixo.
     * @param threshold o nível máximo de estoque para considerar como "estoque baixo"
     * @return total de itens com estoque <= threshold
     */
    long countByCurrentStockLessThanEqual(int threshold);
}
