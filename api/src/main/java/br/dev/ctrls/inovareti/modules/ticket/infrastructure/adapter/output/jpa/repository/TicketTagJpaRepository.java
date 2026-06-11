package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTagJpaRepository extends JpaRepository<TicketTag, UUID> {
    List<TicketTag> findAllByActiveTrue();
    Optional<TicketTag> findByNameIgnoreCase(String name);
}
