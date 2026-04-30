package br.dev.ctrls.inovareti.domain.inventory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDeductionService {

    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * Deduz quantidade do estoque seguindo a política FIFO por lotes.
     * Para cada lote consumido calcula-se o custo: `unit_price * quantidade_consumida`
     * e acumula-se no total. O valor total consumido é salvo no campo
     * `unit_price_at_time` do `StockMovement` para registrar a "verdade financeira"
     * do custo por lote na saída.
     *
     * IMPORTANTE: este método exige que exista uma transação ativa no caller.
     * A anotação `@Transactional(propagation = Propagation.MANDATORY)` força que
     * a dedução ocorra na mesma transação que a operação de fechamento do chamado
     * (por exemplo, `ResolveTicketUseCase.execute(...)`), garantindo atomicidade
     * entre a redução de `current_stock` e a persistência do `StockMovement`.
     *
     * Retorna o valor total (BigDecimal) da dedução, que pode ser utilizado para
     * gerar lançamentos financeiros por centro de custo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal deductWithFifo(UUID itemId, int quantity, String reference) {
        if (quantity <= 0) {
            throw new IllegalStateException("A quantidade a deduzir deve ser maior que zero.");
        }

        Item lockedItem = itemRepository.findByIdForUpdate(itemId)
            .orElseThrow(() -> new NotFoundException("Item não encontrado com id: " + itemId));

        if (lockedItem.getCurrentStock() < quantity) {
                throw new IllegalStateException(
                    "Estoque insuficiente para o item '" + lockedItem.getName() + "'. Estoque atual: "
                        + lockedItem.getCurrentStock() + ", quantidade solicitada: " + quantity);
        }

        List<StockBatch> fifoBatches = stockBatchRepository.findByItemIdOrderByEntryDateAscForUpdate(itemId);
        int remainingToDeduct = quantity;

        // Valor total acumulado (soma de unit_price * qtd_consumida por lote)
        BigDecimal totalValue = BigDecimal.ZERO;

        for (StockBatch batch : fifoBatches) {
            if (remainingToDeduct == 0) {
                break;
            }

            int batchRemaining = batch.getRemainingQuantity();
            if (batchRemaining <= 0) {
                continue;
            }

            int consumedFromBatch = Math.min(batchRemaining, remainingToDeduct);
            batch.setRemainingQuantity(batchRemaining - consumedFromBatch);
            remainingToDeduct -= consumedFromBatch;

            // Calcula o valor consumido deste lote e acumula no total
            if (consumedFromBatch > 0 && batch.getUnitPrice() != null) {
                BigDecimal consumedQty = BigDecimal.valueOf(consumedFromBatch);
                BigDecimal consumedValue = batch.getUnitPrice().multiply(consumedQty);
                totalValue = totalValue.add(consumedValue);
            }
        }

        if (remainingToDeduct > 0) {
            log.warn("Fallback FIFO: {} unidades não puderam ser atendidas a partir dos lotes. Tratando como estoque legado/fantasma para itemId={}, referência={}", 
                remainingToDeduct, itemId, reference);
        }

        stockBatchRepository.saveAll(fifoBatches);

        lockedItem.setCurrentStock(lockedItem.getCurrentStock() - quantity);
        itemRepository.save(lockedItem);

        // Registra o movimento de saída com o valor total apurado no momento da saída.
        StockMovement movement = StockMovement.builder()
            .itemId(itemId)
            .type(StockMovementType.OUT)
            .quantity(quantity)
            .reference(reference)
            .date(LocalDateTime.now())
            .unitPriceAtTime(totalValue)
            .build();
        stockMovementRepository.save(movement);

        log.info("Dedução FIFO aplicada. itemId={}, quantidade={}, referência={}, valorTotal={}", itemId, quantity, reference, totalValue);

        return totalValue;
    }
}
