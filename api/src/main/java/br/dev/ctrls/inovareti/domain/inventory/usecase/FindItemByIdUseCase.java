package br.dev.ctrls.inovareti.domain.inventory.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: busca um item de inventário por seu ID.
 */
@Component
@RequiredArgsConstructor
public class FindItemByIdUseCase {

    private final ItemRepository itemRepository;

    /**
     * Busca um item pelo seu ID, com a categoria carregada.
     *
     * @param id identificador único do item
     * @return DTO com os dados públicos do item
     * @throws NotFoundException se o item não existir
     */
    @Transactional(readOnly = true)
    public ItemResponseDTO execute(UUID id) {
        return itemRepository.findByIdWithCategory(id)
                .map(ItemResponseDTO::from)
                .orElseThrow(() -> new NotFoundException("Item não encontrado"));
    }
}
