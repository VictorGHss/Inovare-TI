package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;

import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ImplementaÃ§Ã£o do Specification Pattern para filtrar FinancialTransactions.
 * Permite construir consultas dinÃ¢micas e reutilizÃ¡veis sem acoplamento ao Controller.
 * <p>Removidos imports redundantes (LocalDateTime, LocalTime) e garantida a integridade da classe.</p>
 */
@Builder
@Observed
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

        // OrdenaÃ§Ã£o padrÃ£o decrescente por data de criaÃ§Ã£o
        query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }
}


