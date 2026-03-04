package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link Ticket}.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Busca todos os chamados com as relações obrigatórias carregadas
     * via JOIN FETCH para evitar o problema N+1.
     */
    @Query("""
            SELECT t FROM Ticket t
            JOIN FETCH t.requester
            JOIN FETCH t.category
            """)
    List<Ticket> findAllWithRelations();

    /**
     * Busca todos os chamados de um solicitante específico, ordenados por data decrescente.
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.requester.id = :requesterId
            ORDER BY t.createdAt DESC
            """)
    List<Ticket> findByRequesterIdOrderByCreatedAtDesc(@Param("requesterId") UUID requesterId);

    List<Ticket> findAllByStatus(TicketStatus status);

    /**
     * Counts tickets by status for analytics.
     * @param status the ticket status to count
     * @return total number of tickets with the given status
     */
    long countByStatus(TicketStatus status);

    /**
     * Counts tickets by requester and status for user-specific analytics.
     */
    long countByRequesterIdAndStatus(@Param("requesterId") UUID requesterId, TicketStatus status);
}
