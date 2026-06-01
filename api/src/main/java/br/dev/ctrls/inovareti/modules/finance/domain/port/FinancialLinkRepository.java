package br.dev.ctrls.inovareti.modules.finance.domain.port;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialLink;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialLink.Canal;

public interface FinancialLinkRepository extends JpaRepository<FinancialLink, UUID> {

    Optional<FinancialLink> findByContaAzulCustomerId(String contaAzulCustomerId);

    Optional<FinancialLink> findByContaAzulCustomerIdAndCanal(String contaAzulCustomerId, Canal canal);

    boolean existsByContaAzulCustomerIdAndCanal(String contaAzulCustomerId, Canal canal);

    Optional<FinancialLink> findByContaAzulCustomerNameIgnoreCase(String contaAzulCustomerName);
}


