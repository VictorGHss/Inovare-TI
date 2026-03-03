package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing ticket attachments.
 */
@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {
    
    /**
     * Finds all attachments for a specific ticket.
     * @param ticketId the UUID of the ticket
     * @return list of attachments for the ticket
     */
    List<TicketAttachment> findByTicketId(UUID ticketId);
}
