package br.dev.ctrls.inovareti.domain.inventory.usecase;

import br.dev.ctrls.inovareti.domain.inventory.ItemCategoryRepository;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemCategoryResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: lista todas as categorias de item de inventário.
 */
@Component
@RequiredArgsConstructor
public class ListAllItemCategoriesUseCase {

    private final ItemCategoryRepository itemCategoryRepository;

    /**
     * Retorna todas as categorias de item cadastradas.
     *
     * @return lista de DTOs com os dados das categorias
     */
    @Transactional(readOnly = true)
    public List<ItemCategoryResponseDTO> execute() {
        return itemCategoryRepository.findAll()
                .stream()
                .map(ItemCategoryResponseDTO::from)
                .toList();
    }
}
