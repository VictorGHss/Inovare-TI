package br.dev.ctrls.inovareti.domain.financeiro;

import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação do Specification Pattern para filtrar FinancialTransactions.
 * Permite construir consultas dinâmicas e reutilizáveis sem acoplamento ao Controller.
 */
@Builder
public class FinancialTransactionSpecification implements Specification<FinancialTransaction> {

    // Campos finais para imutabilidade após construção via @Builder
    private final LocalDate startDate;
    private final LocalDate endDate;

    @Override
    public Predicate toPredicate(jakarta.persistence.criteria.Root<FinancialTransaction> root,
                                 jakarta.persistence.criteria.CriteriaQuery<?> query,
                                 jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        List<Predicate> predicates = new ArrayList<>();

        // Aplica valores padrão se os filtros não forem informados pelo chamador
        LocalDateTime effectiveStartDate = startDate != null
                ? startDate.atStartOfDay()
                : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime effectiveEndDate = endDate != null
                ? endDate.atTime(LocalTime.MAX)
                : LocalDate.now().atTime(LocalTime.MAX);

        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), effectiveStartDate));
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), effectiveEndDate));

        // Ordenação padrão decrescente por data de criação
        query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

        // Usa toArray() sem argumento desnecessário (Java 11+)
        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }
}