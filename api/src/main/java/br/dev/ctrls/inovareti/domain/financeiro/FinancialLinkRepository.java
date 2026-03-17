package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.domain.financeiro.FinancialLink.Canal;

public interface FinancialLinkRepository extends JpaRepository<FinancialLink, UUID> {

    Optional<FinancialLink> findByContaAzulCustomerId(String contaAzulCustomerId);

    Optional<FinancialLink> findByContaAzulCustomerIdAndCanal(String contaAzulCustomerId, Canal canal);

    boolean existsByContaAzulCustomerIdAndCanal(String contaAzulCustomerId, Canal canal);

    Optional<FinancialLink> findByContaAzulCustomerNameIgnoreCase(String contaAzulCustomerName);
}
