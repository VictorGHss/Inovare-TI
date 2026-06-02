package br.dev.ctrls.inovareti.modules.ticket.domain.port.output;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;

public interface TicketRepositoryPort {
    Ticket save(Ticket entity);
    Optional<Ticket> findById(UUID id);
    List<Ticket> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<Ticket> findByShortIdStartingWith(String shortId);
    java.util.List<Ticket> findByRequesterIdAndStatusInOrderByCreatedAtDesc(java.util.UUID requesterId, java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus> statuses);
    java.util.List<Ticket> findAllWithRelations();
    java.util.List<Ticket> findByRequesterIdOrderByCreatedAtDesc(java.util.UUID requesterId);
    java.util.List<Ticket> findAllByStatus(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status);
    java.util.List<Ticket> findSimilarResolvedTickets(java.util.UUID id, java.util.Set<br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag> tags);

    boolean existsByCategoryId(java.util.UUID id);

    java.util.Optional<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> findByIdWithRelations(java.util.UUID id);
    java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> findResolvedRequestTicketsInPeriod(java.time.LocalDateTime start, java.time.LocalDateTime end);
    java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> findByRequesterId(java.util.UUID requesterId);

    long countByStatus(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status);
    long countByRequesterIdAndClosedAtIsNotNull(java.util.UUID requesterId);
    long countByClosedAtIsNotNull();
    java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsByCategory(boolean isAdmin, java.util.UUID requesterId);
    java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsBySector(boolean isAdmin, java.util.UUID requesterId);
    java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsByRequester(boolean isAdmin, java.util.UUID requesterId);
    java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.SectorPriorityMetricView> countTicketsBySectorAndPriority(boolean isAdmin, java.util.UUID requesterId);
    java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countSlaBreachesByCategory(boolean isAdmin, java.util.UUID requesterId);
    java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsByMonth(boolean isAdmin, java.util.UUID requesterId);
    long countByRequesterId(java.util.UUID requesterId);
    long count();

    long countByRequesterIdAndStatus(java.util.UUID requesterId, br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status);
}
