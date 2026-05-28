package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketTagRepository extends JpaRepository<TicketTag, UUID> {
    List<TicketTag> findAllByActiveTrue();
    Optional<TicketTag> findByNameIgnoreCase(String name);
}
