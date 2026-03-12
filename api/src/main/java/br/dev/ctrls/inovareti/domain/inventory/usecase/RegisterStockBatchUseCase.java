package br.dev.ctrls.inovareti.domain.inventory.usecase;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockBatch;
import br.dev.ctrls.inovareti.domain.inventory.StockBatchRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovement;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementType;
import br.dev.ctrls.inovareti.domain.inventory.dto.StockBatchRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.StockBatchResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: registra um lote de entrada de estoque para um item.
 * <p>
 * Esta operação é atômica (@Transactional):
 *   1. Cria o lote com a quantidade informada.
 *   2. Soma a quantidade ao currentStock do item.
 * Se qualquer etapa falhar, toda a transação é revertida.
 */
@Component
@RequiredArgsConstructor
public class RegisterStockBatchUseCase {

    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
        private final StockMovementRepository stockMovementRepository;
        private final AuditLogService auditLogService;

    /**
     * Registra o lote e atualiza o estoque do item.
     *
     * @param request DTO com itemId, quantidade e preço unitário
     * @return DTO com os dados do lote criado
     * @throws NotFoundException se o item não for encontrado
     */
    @Transactional
    public StockBatchResponseDTO execute(StockBatchRequestDTO request) {
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new NotFoundException(
                        "Item não encontrado com o id: " + request.itemId()
                ));

        StockBatch batch = StockBatch.builder()
                .item(item)
                .originalQuantity(request.quantity())
                .remainingQuantity(request.quantity())
                .unitPrice(request.unitPrice())
                .brand(request.brand())
                .supplier(request.supplier())
                .purchaseReason(request.purchaseReason())
                .entryDate(LocalDateTime.now())
                .build();

        stockBatchRepository.save(batch);

        // Atualiza o estoque atual do item somando a quantidade do novo lote
        item.setCurrentStock(item.getCurrentStock() + request.quantity());
        itemRepository.save(item);

        StockMovement movement = StockMovement.builder()
                .itemId(item.getId())
                .type(StockMovementType.IN)
                .quantity(request.quantity())
                .reference("BATCH:" + batch.getId())
                .date(LocalDateTime.now())
                .build();
        stockMovementRepository.save(movement);

        auditLogService.publish(AuditEvent.of(AuditAction.INVENTORY_BATCH_ENTRY)
                .resourceType("StockBatch")
                .resourceId(batch.getId())
                .details("{\"itemId\": \"" + item.getId() + "\", \"quantity\": " + request.quantity() + "}")
                .build());

        return StockBatchResponseDTO.from(batch);
    }
}
