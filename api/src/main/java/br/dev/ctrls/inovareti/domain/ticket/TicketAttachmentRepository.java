package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório para gerenciamento de anexos de chamados.
 */
@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {
    
    /**
     * Retorna todos os anexos de um chamado específico.
     * @param ticketId o UUID do chamado
     * @return lista de anexos do chamado
     */
    List<TicketAttachment> findByTicketId(UUID ticketId);
}
