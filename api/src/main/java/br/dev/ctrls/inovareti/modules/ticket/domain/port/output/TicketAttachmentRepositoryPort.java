package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketAttachment;

public interface TicketAttachmentRepositoryPort {
    TicketAttachment save(TicketAttachment entity);
    Optional<TicketAttachment> findById(UUID id);
    List<TicketAttachment> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<TicketAttachment> findByTicketId(java.util.UUID ticketId);
    java.util.Optional<TicketAttachment> findByStoredFilename(String storedFilename);
}
