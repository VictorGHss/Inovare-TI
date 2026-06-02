package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.ItsmCategoryRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.ItsmCategory;

public interface ItsmCategoryRepositoryPort {
    ItsmCategory save(ItsmCategory entity);
    Optional<ItsmCategory> findById(Integer id);
    List<ItsmCategory> findAll();
    void deleteById(Integer id);
    boolean existsById(Integer id);
    // Add custom methods manually if needed
}
