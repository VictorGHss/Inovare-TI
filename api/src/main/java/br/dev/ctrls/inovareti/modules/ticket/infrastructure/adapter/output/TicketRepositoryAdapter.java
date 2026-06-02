package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.TicketJpaRepository;

@Component
@RequiredArgsConstructor
public class TicketRepositoryAdapter implements TicketRepositoryPort {

    private final TicketJpaRepository repository;

    @Override
    public Ticket save(Ticket entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Ticket> findAll() {
        return repository.findAll();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
    
    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public java.util.List<Ticket> findByShortIdStartingWith(String shortId) {
        return repository.findByShortIdStartingWith(shortId);
    }

    @Override
    public java.util.List<Ticket> findByRequesterIdAndStatusInOrderByCreatedAtDesc(java.util.UUID requesterId, java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus> statuses) {
        return repository.findByRequesterIdAndStatusInOrderByCreatedAtDesc(requesterId, statuses);
    }

    @Override
    public java.util.List<Ticket> findAllWithRelations() {
        return repository.findAllWithRelations();
    }

    @Override
    public java.util.List<Ticket> findByRequesterIdOrderByCreatedAtDesc(java.util.UUID requesterId) {
        return repository.findByRequesterIdOrderByCreatedAtDesc(requesterId);
    }

    @Override
    public java.util.List<Ticket> findAllByStatus(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status) {
        return repository.findAllByStatus(status);
    }

    @Override
    public java.util.List<Ticket> findSimilarResolvedTickets(java.util.UUID id, java.util.Set<br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag> tags) {
        return repository.findSimilarResolvedTickets(id, tags);
    }


    @Override
    public boolean existsByCategoryId(java.util.UUID id) {
        return repository.existsByCategoryId(id);
    }


    @Override
    public java.util.Optional<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> findByIdWithRelations(java.util.UUID id) {
        return repository.findByIdWithRelations(id);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> findResolvedRequestTicketsInPeriod(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return repository.findResolvedRequestTicketsInPeriod(start, end);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket> findByRequesterId(java.util.UUID requesterId) {
        return repository.findByRequesterId(requesterId);
    }


    @Override
    public long countByStatus(br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    public long countByRequesterIdAndClosedAtIsNotNull(java.util.UUID requesterId) {
        return repository.countByRequesterIdAndClosedAtIsNotNull(requesterId);
    }

    @Override
    public long countByClosedAtIsNotNull() {
        return repository.countByClosedAtIsNotNull();
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsByCategory(boolean isAdmin, java.util.UUID requesterId) {
        return repository.countTicketsByCategory(isAdmin, requesterId);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsBySector(boolean isAdmin, java.util.UUID requesterId) {
        return repository.countTicketsBySector(isAdmin, requesterId);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsByRequester(boolean isAdmin, java.util.UUID requesterId) {
        return repository.countTicketsByRequester(isAdmin, requesterId);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.SectorPriorityMetricView> countTicketsBySectorAndPriority(boolean isAdmin, java.util.UUID requesterId) {
        return repository.countTicketsBySectorAndPriority(isAdmin, requesterId);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countSlaBreachesByCategory(boolean isAdmin, java.util.UUID requesterId) {
        return repository.countSlaBreachesByCategory(isAdmin, requesterId);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.domain.analytics.dto.AnalyticsMetricView> countTicketsByMonth(boolean isAdmin, java.util.UUID requesterId) {
        return repository.countTicketsByMonth(isAdmin, requesterId);
    }

    @Override
    public long countByRequesterId(java.util.UUID requesterId) {
        return repository.countByRequesterId(requesterId);
    }

    @Override
    public long count() {
        return repository.count();
    }


    @Override
    public long countByRequesterIdAndStatus(java.util.UUID requesterId, br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status) {
        return repository.countByRequesterIdAndStatus(requesterId, status);
    }

}
