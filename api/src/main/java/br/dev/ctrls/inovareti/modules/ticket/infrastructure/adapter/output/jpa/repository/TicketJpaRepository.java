package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.dev.ctrls.inovareti.modules.analytics.application.dto.AnalyticsMetricView;
import br.dev.ctrls.inovareti.modules.analytics.application.dto.SectorPriorityMetricView;

/**
 * Repositório de acesso a dados para a entidade {@link Ticket}.
 */
public interface TicketJpaRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Busca todos os chamados com as relações obrigatórias carregadas
     * via JOIN FETCH para evitar o problema N+1.
     */
    @Query("""
            SELECT DISTINCT t FROM Ticket t
            JOIN FETCH t.requester r
            LEFT JOIN FETCH r.sector
            JOIN FETCH t.category
            LEFT JOIN FETCH t.requestedItem i
            LEFT JOIN FETCH i.itemCategory
            LEFT JOIN FETCH t.assignedTo
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

    @Query("""
            SELECT DISTINCT t FROM Ticket t
            JOIN FETCH t.requester r
            LEFT JOIN FETCH r.sector
            JOIN FETCH t.category
            JOIN t.tags tag
            WHERE t.status = br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus.RESOLVED
              AND t.id <> :ticketId
              AND tag IN :tags
            """)
    List<Ticket> findSimilarResolvedTickets(@Param("ticketId") UUID ticketId, @Param("tags") java.util.Set<TicketTag> tags);

    @Query("SELECT t FROM Ticket t WHERE CAST(t.id AS string) ILIKE CONCAT(:shortId, '%')")
    List<Ticket> findByShortIdStartingWith(@Param("shortId") String shortId);

    /**
     * Conta chamados por status para analítica.
     * @param status o status do chamado a ser contado
     * @return total de chamados com aquele status
     */
    long countByStatus(TicketStatus status);

        /**
         * Conta chamados com data de fechamento preenchida.
         */
        long countByClosedAtIsNotNull();

        /**
         * Conta chamados de um solicitante com data de fechamento preenchida.
         */
        long countByRequesterIdAndClosedAtIsNotNull(@Param("requesterId") UUID requesterId);

        /**
         * Conta todos os chamados de um solicitante.
         */
        long countByRequesterId(UUID requesterId);

    /**
     * Conta chamados por solicitante e status para analítica individual.
     */
    long countByRequesterIdAndStatus(@Param("requesterId") UUID requesterId, TicketStatus status);

    /**
     * Retorna todos os chamados de um solicitante.
     */
    List<Ticket> findByRequesterId(@Param("requesterId") UUID requesterId);

    @Query(value = """
            select coalesce(tc.name, 'Sem categoria') as name, count(*) as value
            from tickets t
            join ticket_categories tc on tc.id = t.category_id
            where (:restrictToRequester = false or t.requester_id = :userId)
            group by tc.name
            order by count(*) desc, tc.name asc
            """, nativeQuery = true)
    List<AnalyticsMetricView> countTicketsByCategory(
            @Param("restrictToRequester") boolean restrictToRequester,
            @Param("userId") UUID userId);

    @Query(value = """
            select coalesce(s.name, 'Sem setor') as name, count(*) as value
            from tickets t
            join users u on u.id = t.requester_id
            join sectors s on s.id = u.sector_id
            where (:restrictToRequester = false or t.requester_id = :userId)
            group by s.name
            order by count(*) desc, s.name asc
            """, nativeQuery = true)
    List<AnalyticsMetricView> countTicketsBySector(
            @Param("restrictToRequester") boolean restrictToRequester,
            @Param("userId") UUID userId);

    @Query(value = """
            select coalesce(u.name, 'Sem solicitante') as name, count(*) as value
            from tickets t
            join users u on u.id = t.requester_id
            where (:restrictToRequester = false or t.requester_id = :userId)
            group by u.name
            order by count(*) desc, u.name asc
            """, nativeQuery = true)
    List<AnalyticsMetricView> countTicketsByRequester(
            @Param("restrictToRequester") boolean restrictToRequester,
            @Param("userId") UUID userId);

    @Query(value = """
            SELECT sector, priority, value FROM (
                SELECT coalesce(s.name, 'Sem setor') as sector,
                       CASE
                           WHEN t.priority IN ('HIGH', 'URGENT') THEN 'Alta'
                           WHEN t.priority = 'NORMAL' THEN 'Média'
                           ELSE 'Baixa'
                       END as priority,
                       COUNT(*) as value
                FROM tickets t
                JOIN users u ON u.id = t.requester_id
                JOIN sectors s ON s.id = u.sector_id
                WHERE (:restrictToRequester = false OR t.requester_id = :userId)
                GROUP BY coalesce(s.name, 'Sem setor'),
                         CASE
                             WHEN t.priority IN ('HIGH', 'URGENT') THEN 'Alta'
                             WHEN t.priority = 'NORMAL' THEN 'Média'
                             ELSE 'Baixa'
                         END
            ) as subquery
            ORDER BY sector ASC,
                     CASE
                         WHEN priority = 'Baixa' THEN 1
                         WHEN priority = 'Média' THEN 2
                         ELSE 3
                     END ASC
            """, nativeQuery = true)
    List<SectorPriorityMetricView> countTicketsBySectorAndPriority(
            @Param("restrictToRequester") boolean restrictToRequester,
            @Param("userId") UUID userId);

    @Query(value = """
            select coalesce(tc.name, 'Sem categoria') as name, count(*) as value
            from tickets t
            join ticket_categories tc on tc.id = t.category_id
            where (:restrictToRequester = false or t.requester_id = :userId)
              and t.closed_at is not null
              and t.closed_at > t.sla_deadline
              and t.status in ('RESOLVED', 'CLOSED')
            group by tc.name
            order by count(*) desc, tc.name asc
            """, nativeQuery = true)
    List<AnalyticsMetricView> countSlaBreachesByCategory(
            @Param("restrictToRequester") boolean restrictToRequester,
            @Param("userId") UUID userId);

    @Query(value = """
            select to_char(date_trunc('month', t.created_at), 'YYYY-MM-01') as name, count(*) as value
            from tickets t
            where (:restrictToRequester = false or t.requester_id = :userId)
            group by date_trunc('month', t.created_at)
            order by date_trunc('month', t.created_at) asc
            """, nativeQuery = true)
    List<AnalyticsMetricView> countTicketsByMonth(
            @Param("restrictToRequester") boolean restrictToRequester,
            @Param("userId") UUID userId);

        /**
         * Retorna os chamados de um solicitante filtrados por status, mais recentes primeiro.
         */
        List<Ticket> findByRequesterIdAndStatusInOrderByCreatedAtDesc(
                        @Param("requesterId") UUID requesterId,
                        @Param("statusList") List<TicketStatus> statusList
        );

            /**
             * Busca um chamado por id carregando relações necessárias para notificações
             * (requester + requester.sector + category + assignedTo) via JOIN FETCH
             * para evitar LazyInitializationException quando usado em métodos assíncronos.
             */
            @Query("""
                    SELECT t FROM Ticket t
                    JOIN FETCH t.requester r
                    JOIN FETCH r.sector
                    JOIN FETCH t.category
                    LEFT JOIN FETCH t.assignedTo a
                    WHERE t.id = :id
                    """)
            Optional<Ticket> findByIdWithRelations(@Param("id") UUID id);

    /** Verifica se há tickets vinculados a uma categoria antes de permitir a exclusão. */
    boolean existsByCategoryId(UUID categoryId);

    /**
     * Busca chamados de solicitação (com requestedItem preenchido) com status RESOLVED
     * encerrados no intervalo informado, carregando todas as relações necessárias
     * para o relatório de saídas (requester + sector + requestedItem + itemCategory).
     */
    @Query("""
            SELECT DISTINCT t FROM Ticket t
            JOIN FETCH t.requester r
            LEFT JOIN FETCH r.sector
            JOIN FETCH t.category
            JOIN FETCH t.requestedItem i
            LEFT JOIN FETCH i.itemCategory
            LEFT JOIN FETCH t.assignedTo
            WHERE t.status = 'RESOLVED'
              AND t.requestedItem IS NOT NULL
              AND t.closedAt BETWEEN :start AND :end
            ORDER BY t.closedAt ASC
            """)
    List<Ticket> findResolvedRequestTicketsInPeriod(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);
}
