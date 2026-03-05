package br.dev.ctrls.inovareti.domain.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.inventory.StockBatch;

/**
 * DTO de saída com os dados de um lote de estoque registrado.
 */
public record StockBatchResponseDTO(
        UUID id,
        UUID itemId,
        String itemName,
        Integer originalQuantity,
        Integer remainingQuantity,
        BigDecimal unitPrice,
        String brand,
        String supplier,
        String purchaseReason,
        LocalDateTime entryDate,
        String invoiceFileName
) {
    /** Converte uma entidade {@link StockBatch} para este DTO. */
    public static StockBatchResponseDTO from(StockBatch batch) {
        return new StockBatchResponseDTO(
                batch.getId(),
                batch.getItem().getId(),
                batch.getItem().getName(),
                batch.getOriginalQuantity(),
                batch.getRemainingQuantity(),
                batch.getUnitPrice(),
                batch.getBrand(),
                batch.getSupplier(),
                batch.getPurchaseReason(),
                batch.getEntryDate(),
                batch.getInvoiceFileName()
        );
    }
}
