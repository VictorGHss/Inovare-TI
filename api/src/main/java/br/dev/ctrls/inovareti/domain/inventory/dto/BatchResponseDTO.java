package br.dev.ctrls.inovareti.domain.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.inventory.StockBatch;

/**
 * DTO simplificado para exibição do histórico de lotes de um item específico.
 */
public record BatchResponseDTO(
        UUID id,
        Integer originalQuantity,
        Integer remainingQuantity,
        BigDecimal unitPrice,
        LocalDateTime entryDate
) {
    /** Converte uma entidade {@link StockBatch} para este DTO. */
    public static BatchResponseDTO from(StockBatch batch) {
        return new BatchResponseDTO(
                batch.getId(),
                batch.getOriginalQuantity(),
                batch.getRemainingQuantity(),
                batch.getUnitPrice(),
                batch.getEntryDate()
        );
    }
}
