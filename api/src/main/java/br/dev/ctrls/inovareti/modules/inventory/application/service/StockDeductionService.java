package br.dev.ctrls.inovareti.modules.inventory.application.service;
import io.micrometer.observation.annotation.Observed;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType;

import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;

import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort;

import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockBatchRepositoryPort;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class StockDeductionService {

    private final ItemRepositoryPort itemRepository;
    private final StockBatchRepositoryPort stockBatchRepository;
    private final StockMovementRepositoryPort stockMovementRepository;

    /**
     * Deduz quantidade do estoque seguindo a polÃ­tica FIFO por lotes.
     * Para cada lote consumido calcula-se o custo: `unit_price * quantidade_consumida`
     * e acumula-se no total. O valor total consumido Ã© salvo no campo
     * `unit_price_at_time` do `StockMovement` para registrar a "verdade financeira"
     * do custo por lote na saÃ­da.
     *
     * IMPORTANTE: este mÃ©todo exige que exista uma transaÃ§Ã£o ativa no caller.
     * A anotaÃ§Ã£o `@Transactional(propagation = Propagation.MANDATORY)` forÃ§a que
     * a deduÃ§Ã£o ocorra na mesma transaÃ§Ã£o que a operaÃ§Ã£o de fechamento do chamado
     * (por exemplo, `ResolveTicketUseCase.execute(...)`), garantindo atomicidade
     * entre a reduÃ§Ã£o de `current_stock` e a persistÃªncia do `StockMovement`.
     *
     * Retorna o valor total (BigDecimal) da deduÃ§Ã£o, que pode ser utilizado para
     * gerar lanÃ§amentos financeiros por centro de custo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal deductWithFifo(UUID itemId, int quantity, String reference, UUID recipientUserId) {
        if (quantity <= 0) {
            throw new IllegalStateException("A quantidade a deduzir deve ser maior que zero.");
        }

        Item lockedItem = itemRepository.findByIdForUpdate(itemId)
            .orElseThrow(() -> new NotFoundException("Item nÃ£o encontrado com id: " + itemId));

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
            log.warn("Fallback FIFO: {} unidades nÃ£o puderam ser atendidas a partir dos lotes. Tratando como estoque legado/fantasma para itemId={}, referÃªncia={}", 
                remainingToDeduct, itemId, reference);
        }

        stockBatchRepository.saveAll(fifoBatches);

        lockedItem.setCurrentStock(lockedItem.getCurrentStock() - quantity);
        itemRepository.save(lockedItem);

        // Registra o movimento de saÃ­da com o valor total apurado no momento da saÃ­da.
        StockMovement movement = StockMovement.builder()
            .itemId(itemId)
            .type(StockMovementType.OUT)
            .quantity(quantity)
            .reference(reference)
            .date(LocalDateTime.now())
            .unitPriceAtTime(totalValue)
            .recipientUserId(recipientUserId)
            .build();
        stockMovementRepository.save(movement);

        log.info("DeduÃ§Ã£o FIFO aplicada. itemId={}, quantidade={}, referÃªncia={}, valorTotal={}", itemId, quantity, reference, totalValue);

        return totalValue;
    }
}


