package br.dev.ctrls.inovareti.modules.ticket.application.service;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority;

/**
 * Ficheiro que define a especificação para filtrar chamados na camada de persistência.
 * Fornece métodos para criar filtros dinâmicos para a pesquisa global.
 */
public class TicketSpecification {

    /**
     * Constrói uma especificação combinada para filtrar chamados com base no utilizador solicitante,
     * tags, termo de pesquisa global, status, prioridade e categoria.
     *
     * @param requesterId identificador opcional do utilizador solicitante para isolamento de dados
     * @param tagIds lista opcional de identificadores de tags
     * @param search termo opcional para a pesquisa global (título ou descrição)
     * @param status status opcional do chamado
     * @param priority prioridade opcional do chamado
     * @param categoryId identificador opcional da categoria do chamado
     * @return a especificação JPA configurada
     */
    public static Specification<Ticket> filterTickets(
            UUID requesterId,
            List<UUID> tagIds,
            String search,
            TicketStatus status,
            TicketPriority priority,
            UUID categoryId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Garante o isolamento: se for um utilizador comum, só vê os seus próprios chamados
            if (requesterId != null) {
                predicates.add(cb.equal(root.get("requester").get("id"), requesterId));
            }

            // Filtro por etiquetas/tags usando subquery para manter paginação correta no banco
            if (tagIds != null && !tagIds.isEmpty()) {
                var subquery = query.subquery(UUID.class);
                var subRoot = subquery.from(Ticket.class);
                var subJoin = subRoot.join("tags");
                subquery.select(subRoot.get("id"))
                        .where(subJoin.get("id").in(tagIds));
                predicates.add(root.get("id").in(subquery));
            }

            // Pesquisa global no título ou descrição
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate titlePredicate = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descriptionPredicate = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titlePredicate, descriptionPredicate));
            }

            // Filtro dinâmico por status
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Filtro dinâmico por prioridade
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            // Filtro dinâmico por categoria
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
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
