package br.dev.ctrls.inovareti.domain.inventory.usecase;

import java.util.HashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategory;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategoryRepository;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: cria um novo item de inventário.
 * O estoque inicial sempre começa em zero.
 * Use {@link RegisterStockBatchUseCase} para registrar entradas de estoque.
 */
@Component
@RequiredArgsConstructor
public class CreateItemUseCase {

    private final ItemRepository itemRepository;
    private final ItemCategoryRepository itemCategoryRepository;
        private final AuditLogService auditLogService;

    /**
     * Executa a criação do item.
     *
     * @param request DTO com os dados do item
     * @return DTO com os dados do item criado
     * @throws NotFoundException se a categoria não existir
     */
    @Transactional
    public ItemResponseDTO execute(ItemRequestDTO request) {
        ItemCategory category = itemCategoryRepository.findById(request.itemCategoryId())
                .orElseThrow(() -> new NotFoundException(
                        "Categoria de item não encontrada com o id: " + request.itemCategoryId()
                ));

        Item item = Item.builder()
                .itemCategory(category)
                .name(request.name())
                .currentStock(0)
                .specifications(request.specifications() != null
                        ? request.specifications()
                        : new HashMap<>())
                .build();

        Item savedItem = itemRepository.save(item);
        auditLogService.publish(AuditEvent.of(AuditAction.INVENTORY_ITEM_CREATE)
                .resourceType("Item")
                .resourceId(savedItem.getId())
                .details("{\"name\": \"" + savedItem.getName() + "\"}")
                .build());

        return ItemResponseDTO.from(savedItem);
    }
}
