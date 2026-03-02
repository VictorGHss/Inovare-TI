package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link ItemCategory}.
 */
@Repository
public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {

    Optional<ItemCategory> findByName(String name);

    boolean existsByName(String name);

    List<ItemCategory> findAllByIsConsumable(Boolean isConsumable);
}
