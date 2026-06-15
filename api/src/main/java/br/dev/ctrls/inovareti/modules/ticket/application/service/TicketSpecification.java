package br.dev.ctrls.inovareti.modules.ticket.application.service;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketTag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ficheiro que define a especificação para filtrar chamados na camada de persistência.
 * Fornece métodos para criar filtros dinâmicos para a pesquisa global.
 */
public class TicketSpecification {

    /**
     * Constrói uma especificação combinada para filtrar chamados com base no utilizador solicitante,
     * identificadores de tags e um termo para pesquisa global nos campos de título e descrição.
     *
     * @param requesterId identificador opcional do utilizador solicitante para isolamento de dados
     * @param tagIds lista opcional de identificadores de tags
     * @param search termo opcional para a pesquisa global (título ou descrição)
     * @return a especificação JPA configurada
     */
    public static Specification<Ticket> filterTickets(UUID requesterId, List<UUID> tagIds, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Garante o isolamento: se for um utilizador comum, só vê os seus próprios chamados
            if (requesterId != null) {
                predicates.add(cb.equal(root.get("requester").get("id"), requesterId));
            }

            // Filtro por etiquetas/tags
            if (tagIds != null && !tagIds.isEmpty()) {
                Join<Ticket, TicketTag> tagsJoin = root.join("tags");
                predicates.add(tagsJoin.get("id").in(tagIds));
                query.distinct(true);
            }

            // Pesquisa global no título ou descrição (contendo o termo, ignorando maiúsculas/minúsculas)
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate titlePredicate = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descriptionPredicate = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titlePredicate, descriptionPredicate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
