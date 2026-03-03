package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    /**
     * Busca todos os comentários de um chamado específico, ordenados por data de criação.
     * Carrega as relações com author via JOIN FETCH para evitar problema N+1.
     */
    @Query("SELECT tc FROM TicketComment tc "
            + "JOIN FETCH tc.author "
            + "WHERE tc.ticket.id = :ticketId "
            + "ORDER BY tc.createdAt ASC")
    List<TicketComment> findByTicketIdWithAuthor(UUID ticketId);
}
