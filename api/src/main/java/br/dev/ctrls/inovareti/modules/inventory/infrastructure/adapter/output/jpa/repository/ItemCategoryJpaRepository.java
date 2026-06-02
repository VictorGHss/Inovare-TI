package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemCategory;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link ItemCategory}.
 */
@Repository
public interface ItemCategoryJpaRepository extends JpaRepository<ItemCategory, UUID> {

    Optional<ItemCategory> findByName(String name);

    boolean existsByName(String name);

    List<ItemCategory> findAllByIsConsumable(Boolean isConsumable);
}
