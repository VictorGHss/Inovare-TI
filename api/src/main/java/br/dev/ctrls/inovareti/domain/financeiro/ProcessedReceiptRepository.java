package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedReceiptRepository extends JpaRepository<ProcessedReceipt, UUID> {

    boolean existsByParcelaId(String parcelaId);

    Optional<ProcessedReceipt> findByParcelaId(String parcelaId);

    List<ProcessedReceipt> findByStatus(ProcessedReceiptStatus status);

    List<ProcessedReceipt> findAllByOrderByProcessedAtDesc();
}
