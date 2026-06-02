package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.ItsmCategory;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para {@link ItsmCategory}.
 */
@Repository
public interface ItsmCategoryJpaRepository extends JpaRepository<ItsmCategory, Integer> {

    /** Busca uma categoria ITSM pelo nome exato. */
    Optional<ItsmCategory> findByName(String name);
}
