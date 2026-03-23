package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingAttemptRepository extends JpaRepository<ProcessingAttempt, UUID> {

    Optional<ProcessingAttempt> findBySaleId(String saleId);

    void deleteBySaleId(String saleId);
}
