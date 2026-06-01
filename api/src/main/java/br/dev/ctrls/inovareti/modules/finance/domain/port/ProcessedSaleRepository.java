package br.dev.ctrls.inovareti.modules.finance.domain.port;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedSale;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedSaleRepository extends JpaRepository<ProcessedSale, UUID> {

    boolean existsBySaleId(String saleId);

    @Query("select max(ps.processedAt) from ProcessedSale ps")
    Optional<LocalDateTime> findMaxProcessedAt();
}
