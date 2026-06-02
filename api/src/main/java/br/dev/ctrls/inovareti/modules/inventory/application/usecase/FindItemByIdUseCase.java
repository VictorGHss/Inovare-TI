package br.dev.ctrls.inovareti.modules.inventory.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: busca um item de inventário por seu ID.
 */
@Component
@RequiredArgsConstructor
public class FindItemByIdUseCase {

    private final ItemRepositoryPort itemRepository;

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
