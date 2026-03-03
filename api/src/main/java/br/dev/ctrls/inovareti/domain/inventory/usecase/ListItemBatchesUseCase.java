package br.dev.ctrls.inovareti.domain.inventory.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockBatchRepository;
import br.dev.ctrls.inovareti.domain.inventory.dto.BatchResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: lista todos os lotes de estoque de um item específico.
 * Os lotes são retornados do mais recente para o mais antigo.
 */
@Component
@RequiredArgsConstructor
public class ListItemBatchesUseCase {

    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;

    /**
     * Busca todos os lotes de estoque de um item, ordenados por data de entrada decrescente.
     *
     * @param itemId identificador único do item
     * @return lista de DTOs com os dados dos lotes
     * @throws NotFoundException se o item não existir
     */
    @Transactional(readOnly = true)
    public List<BatchResponseDTO> execute(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item não encontrado"));

        return stockBatchRepository.findByItemOrderByEntryDateDesc(item)
                .stream()
                .map(BatchResponseDTO::from)
                .toList();
    }
}
