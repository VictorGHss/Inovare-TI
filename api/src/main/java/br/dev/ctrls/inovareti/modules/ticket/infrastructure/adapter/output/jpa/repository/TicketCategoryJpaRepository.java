package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketCategory;


import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório de acesso a dados para a entidade {@link TicketCategory}.
 */
public interface TicketCategoryJpaRepository extends JpaRepository<TicketCategory, UUID> {

    Optional<TicketCategory> findByName(String name);

    boolean existsByName(String name);
}

