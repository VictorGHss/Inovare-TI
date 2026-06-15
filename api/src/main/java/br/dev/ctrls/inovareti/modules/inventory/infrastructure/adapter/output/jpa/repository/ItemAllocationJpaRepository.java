package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemAllocationEntity;

/**
 * Repositório Spring Data JPA para gerenciamento da persistência de alocações ({@link ItemAllocationEntity}).
 */
@Repository
public interface ItemAllocationJpaRepository extends JpaRepository<ItemAllocationEntity, UUID> {
    
    /**
     * Busca alocações pelo ID do item filho (consumível/periférico alocado) ordenada por data decrescente.
     */
    List<ItemAllocationEntity> findByChildItemIdOrderByAllocatedAtDesc(UUID childItemId);

    /**
     * Busca alocações pelo ID do item pai (ativo principal que recebeu a alocação) ordenada por data decrescente.
     */
    List<ItemAllocationEntity> findByParentItemIdOrderByAllocatedAtDesc(UUID parentItemId);
}
