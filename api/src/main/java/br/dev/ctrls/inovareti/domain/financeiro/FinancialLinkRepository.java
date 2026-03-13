package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialLinkRepository extends JpaRepository<FinancialLink, UUID> {

    Optional<FinancialLink> findByContaAzulCustomerId(String contaAzulCustomerId);
}
