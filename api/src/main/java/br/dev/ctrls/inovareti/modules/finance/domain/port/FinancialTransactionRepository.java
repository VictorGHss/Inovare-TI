package br.dev.ctrls.inovareti.modules.finance.domain.port;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID>, JpaSpecificationExecutor<FinancialTransaction> {

	java.util.List<FinancialTransaction> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);

	java.util.List<FinancialTransaction> findByTicketId(UUID ticketId);

	    /**
	     * Busca lançamentos financeiros filtrando por resource_type, target_type,
	     * target_id e intervalo de data. Usado pelo relatório de saídas para
	     * garantir a "Verdade Financeira" (resource_type = INVENTORY e targets
	     * DOCTOR/SECTOR dentro do dia UTC do fechamento do ticket).
	     */
	    java.util.List<FinancialTransaction> findByResourceTypeAndTargetTypeAndTargetIdAndCreatedAtBetween(
		    FinancialTransaction.ResourceType resourceType,
		    FinancialTransaction.TargetType targetType,
		    UUID targetId,
		    java.time.LocalDateTime start,
		    java.time.LocalDateTime end
	    );

}

