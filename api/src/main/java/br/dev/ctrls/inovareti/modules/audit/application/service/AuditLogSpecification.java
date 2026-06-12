package br.dev.ctrls.inovareti.modules.audit.application.service;

import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import org.springframework.data.jpa.domain.Specification;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditLog;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementação do Specification Pattern para filtrar AuditLogs.
 * Permite construir consultas dinâmicas e reutilizáveis.
 */
@Builder
public class AuditLogSpecification implements Specification<AuditLog> {

    private final UUID userId;
    private final AuditAction action;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    @Override
    public Predicate toPredicate(jakarta.persistence.criteria.Root<AuditLog> root,
                                 jakarta.persistence.criteria.CriteriaQuery<?> query,
                                 jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        List<Predicate> predicates = new ArrayList<>();

        if (userId != null) {
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
        }

        if (action != null) {
            predicates.add(criteriaBuilder.equal(root.get("action"), action));
        }

        if (startDate != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }

        if (endDate != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }

        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }
}
