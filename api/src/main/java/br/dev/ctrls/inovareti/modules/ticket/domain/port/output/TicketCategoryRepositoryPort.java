package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketCategory;



public interface TicketCategoryRepositoryPort {
    TicketCategory save(TicketCategory entity);
    Optional<TicketCategory> findById(UUID id);
    List<TicketCategory> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    boolean existsByName(String name);
}
