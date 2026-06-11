package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketAttachment;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório para gerenciamento de anexos de chamados.
 */
public interface TicketAttachmentJpaRepository extends JpaRepository<TicketAttachment, UUID> {
    
    /**
     * Retorna todos os anexos de um chamado específico.
     * @param ticketId o UUID do chamado
     * @return lista de anexos do chamado
     */
    List<TicketAttachment> findByTicketId(UUID ticketId);

    /**
     * Busca um anexo pelo nome único em disco.
     * @param storedFilename nome físico em disco
     * @return optional com o anexo encontrado
     */
    Optional<TicketAttachment> findByStoredFilename(String storedFilename);
}
