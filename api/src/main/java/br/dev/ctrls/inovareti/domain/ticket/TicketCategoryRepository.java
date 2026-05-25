package br.dev.ctrls.inovareti.domain.ticket;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link TicketCategory}.
 */
@Repository
public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {

    Optional<TicketCategory> findByName(String name);

    boolean existsByName(String name);

    /** Verifica se há tickets associados a uma categoria antes de permitir a exclusão. */
    boolean existsByCategory_Id(UUID categoryId);
}

