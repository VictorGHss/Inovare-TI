package br.dev.ctrls.inovareti.modules.inventory.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: lista itens de inventário com suporte a filtro e ordenação.
 * Usa paginação e ordenação do Spring Data para consultas mais eficientes.
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("spring-data-string-property-reference")
public class ListAllItemsUseCase {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_CURRENT_STOCK = "currentStock";

    private static final int LOW_STOCK_THRESHOLD = 3;

    private final ItemRepositoryPort itemRepository;

    /**
     * Retorna itens de inventário com suporte a filtro de estoque baixo e ordenação.
     *
     * @param sortField     campo de ordenação (name, currentStock, oldestBatchEntryDate)
     * @param sortDirection direção da ordenação
     * @param lowStockOnly  quando true, retorna apenas itens no limiar de estoque baixo
     * @param page          número da página (base 0)
     * @param size          tamanho da página
     * @return lista de DTOs com os dados públicos dos itens filtrados
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ItemResponseDTO> execute(
            String sortField,
            Sort.Direction sortDirection,
            boolean lowStockOnly,
            org.springframework.data.domain.Pageable pageable) {

        Sort.Direction safeDirection = sortDirection == null ? Sort.Direction.ASC : sortDirection;
        String safeSortField = normalizeSortField(sortField);

        org.springframework.data.domain.Pageable effectivePageable;
        if ("oldestBatchEntryDate".equals(safeSortField)) {
            effectivePageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        } else {
            Sort sort = Sort.by(safeDirection, safeSortField).and(Sort.by(Sort.Direction.ASC, FIELD_NAME));
            effectivePageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }

        Page<Item> itemsPage;

        if ("oldestBatchEntryDate".equals(safeSortField)) {
            itemsPage = safeDirection.isAscending()
                    ? itemRepository.findAllOrderByOldestBatchEntryDateAsc(lowStockOnly, LOW_STOCK_THRESHOLD, effectivePageable)
                    : itemRepository.findAllOrderByOldestBatchEntryDateDesc(lowStockOnly, LOW_STOCK_THRESHOLD, effectivePageable);
        } else {
            itemsPage = lowStockOnly
                    ? itemRepository.findByCurrentStockLessThanEqual(LOW_STOCK_THRESHOLD, effectivePageable)
                    : itemRepository.findAll(effectivePageable);
        }

        return itemsPage.map(ItemResponseDTO::from);
    }

    private String normalizeSortField(String sortField) {
        if (FIELD_NAME.equals(sortField)) {
            return FIELD_NAME;
        }
        if (FIELD_CURRENT_STOCK.equals(sortField)) {
            return FIELD_CURRENT_STOCK;
        }
        if ("oldestBatchEntryDate".equals(sortField)) {
            return "oldestBatchEntryDate";
        }
        throw new BadRequestException("Campo de ordenação inválido para listagem de itens.");
    }
}
