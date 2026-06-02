package br.dev.ctrls.inovareti.modules.inventory.application.usecase;

import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemCategoryResponseDTO;
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

    private final ItemCategoryRepositoryPort itemCategoryRepository;

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
