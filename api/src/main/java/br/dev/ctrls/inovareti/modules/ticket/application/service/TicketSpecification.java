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

    /**
     * Constrói uma especificação JPA para buscar todos os chamados que possuem vínculo com um item
     * específico do inventário de TI (seja o item solicitado principal ou qualquer item na lista associativa).
     *
     * @param itemId O identificador único do item de inventário (UUID)
     * @return a especificação JPA configurada
     */
    public static Specification<Ticket> byItemId(java.util.UUID itemId) {
        return (root, query, cb) -> {
            if (itemId == null) {
                return cb.conjunction();
            }

            // Realiza um join (LEFT JOIN) com a tabela associativa de múltiplos itens
            var join = root.join("requestedItems", jakarta.persistence.criteria.JoinType.LEFT);

            // Condição 1: O item é o item principal solicitado diretamente no chamado (requestedItem)
            jakarta.persistence.criteria.Predicate isRequestedItemDirect = cb.equal(root.get("requestedItem").get("id"), itemId);

            // Condição 2: O item está contido na lista associativa de múltiplos itens (requestedItems)
            jakarta.persistence.criteria.Predicate isRequestedItemInList = cb.equal(join.get("item").get("id"), itemId);

            // Garante que não haverá duplicidade de linhas no resultado
            query.distinct(true);

            // Retorna se atende a qualquer uma das duas condições
            return cb.or(isRequestedItemDirect, isRequestedItemInList);
        };
    }
}
