package br.dev.ctrls.inovareti.modules.inventory.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
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
public class ListAllItemsUseCase {

    private static final int LOW_STOCK_THRESHOLD = 3;
    private static final int MAX_PAGE_SIZE = 500;

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
    public List<ItemResponseDTO> execute(
            String sortField,
            Sort.Direction sortDirection,
            boolean lowStockOnly,
            int page,
            int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction safeDirection = sortDirection == null ? Sort.Direction.ASC : sortDirection;
        String safeSortField = normalizeSortField(sortField);

        Page<Item> itemsPage;

        if ("oldestBatchEntryDate".equals(safeSortField)) {
            PageRequest pageable = PageRequest.of(safePage, safeSize);
            itemsPage = safeDirection.isAscending()
                    ? itemRepository.findAllOrderByOldestBatchEntryDateAsc(lowStockOnly, LOW_STOCK_THRESHOLD, pageable)
                    : itemRepository.findAllOrderByOldestBatchEntryDateDesc(lowStockOnly, LOW_STOCK_THRESHOLD, pageable);
        } else {
            Sort sort = Sort.by(safeDirection, safeSortField).and(Sort.by(Sort.Direction.ASC, "name"));
            PageRequest pageable = PageRequest.of(safePage, safeSize, sort);
            itemsPage = lowStockOnly
                    ? itemRepository.findByCurrentStockLessThanEqual(LOW_STOCK_THRESHOLD, pageable)
                    : itemRepository.findAll(pageable);
        }

        return itemsPage
                .stream()
                .map(ItemResponseDTO::from)
                .toList();
    }

    private String normalizeSortField(String sortField) {
        if ("name".equals(sortField) || "currentStock".equals(sortField) || "oldestBatchEntryDate".equals(sortField)) {
            return sortField;
        }
        throw new BadRequestException("Campo de ordenação inválido para listagem de itens.");
    }
}
