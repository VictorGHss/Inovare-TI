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
            throw new IllegalStateException("Quantity to deduct must be greater than zero.");
        }

        Item lockedItem = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemId));

        if (lockedItem.getCurrentStock() < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for item '" + lockedItem.getName() + "'. Current stock: "
                            + lockedItem.getCurrentStock() + ", requested quantity: " + quantity);
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
            throw new IllegalStateException(
                    "Stock batches could not satisfy FIFO deduction. Missing quantity: " + remainingToDeduct);
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

        log.info("FIFO stock deduction applied. itemId={}, quantity={}, reference={}", itemId, quantity, reference);
    }
}
