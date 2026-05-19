package br.dev.ctrls.inovareti.domain.financeiro;

import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação do Specification Pattern para filtrar FinancialTransactions.
 * Permite construir consultas dinâmicas e reutilizáveis sem acoplamento ao Controller.
 * <p>Removidos imports redundantes (LocalDateTime, LocalTime) e garantida a integridade da classe.</p>
 */
@Builder
public class FinancialTransactionSpecification implements Specification<FinancialTransaction> {

    private final LocalDate startDate;
    private final LocalDate endDate;

    @Override
    public Predicate toPredicate(jakarta.persistence.criteria.Root<FinancialTransaction> root,
                                 jakarta.persistence.criteria.CriteriaQuery<?> query,
                                 jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        List<Predicate> predicates = new ArrayList<>();

        // Aplica filtros se informados
        if (startDate != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        
        if (endDate != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }

        // Ordenação padrão decrescente por data de criação
        query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }
}