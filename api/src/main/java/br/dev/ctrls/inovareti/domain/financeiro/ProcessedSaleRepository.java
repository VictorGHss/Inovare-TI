package br.dev.ctrls.inovareti.domain.financeiro;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedSaleRepository extends JpaRepository<ProcessedSale, UUID> {

    boolean existsBySaleId(String saleId);
}