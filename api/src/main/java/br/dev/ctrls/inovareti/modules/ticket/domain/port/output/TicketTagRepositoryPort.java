package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;



public interface TicketTagRepositoryPort {
    TicketTag save(TicketTag entity);
    Optional<TicketTag> findById(UUID id);
    List<TicketTag> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<TicketTag> findAllByActiveTrue();
    java.util.Optional<TicketTag> findByNameIgnoreCase(String name);
    void delete(TicketTag tag);
}
