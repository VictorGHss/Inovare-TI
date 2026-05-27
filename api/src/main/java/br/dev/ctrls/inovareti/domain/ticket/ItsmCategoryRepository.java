package br.dev.ctrls.inovareti.domain.ticket;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para {@link ItsmCategory}.
 */
@Repository
public interface ItsmCategoryRepository extends JpaRepository<ItsmCategory, Integer> {

    /** Busca uma categoria ITSM pelo nome exato. */
    Optional<ItsmCategory> findByName(String name);
}
