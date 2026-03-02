package br.dev.ctrls.inovareti.domain.inventory.usecase;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategory;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategoryRepository;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemCategoryRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemCategoryResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: cria uma nova categoria de item de inventário.
 * Garante unicidade do nome antes de persistir.
 */
@Component
@RequiredArgsConstructor
public class CreateItemCategoryUseCase {

    private final ItemCategoryRepository itemCategoryRepository;

    /**
     * Executa a criação da categoria de item.
     *
     * @param request DTO com nome e flag de consumível
     * @return DTO com os dados da categoria criada
     * @throws ConflictException se já existir uma categoria com o mesmo nome
     */
    @Transactional
    public ItemCategoryResponseDTO execute(ItemCategoryRequestDTO request) {
        if (itemCategoryRepository.existsByName(request.name())) {
            throw new ConflictException(
                    "Já existe uma categoria de item com o nome: " + request.name()
            );
        }

        ItemCategory category = ItemCategory.builder()
                .name(request.name())
                .isConsumable(request.isConsumable())
                .build();

        return ItemCategoryResponseDTO.from(itemCategoryRepository.save(category));
    }
}
