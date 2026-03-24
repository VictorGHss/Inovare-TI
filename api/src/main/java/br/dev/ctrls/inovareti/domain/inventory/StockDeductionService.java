package br.dev.ctrls.inovareti.domain.inventory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void deductWithFifo(UUID itemId, int quantity, String reference) {
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
        }

        if (remainingToDeduct > 0) {
            log.warn("Fallback FIFO: {} unidades não puderam ser atendidas a partir dos lotes. Tratando como estoque legado/fantasma para itemId={}, referência={}", 
                remainingToDeduct, itemId, reference);
        }

        stockBatchRepository.saveAll(fifoBatches);

        lockedItem.setCurrentStock(lockedItem.getCurrentStock() - quantity);
        itemRepository.save(lockedItem);

        StockMovement movement = StockMovement.builder()
                .itemId(itemId)
                .type(StockMovementType.OUT)
                .quantity(quantity)
                .reference(reference)
                .date(LocalDateTime.now())
                .build();
        stockMovementRepository.save(movement);

        log.info("Dedução FIFO aplicada. itemId={}, quantidade={}, referência={}", itemId, quantity, reference);
    }
}
