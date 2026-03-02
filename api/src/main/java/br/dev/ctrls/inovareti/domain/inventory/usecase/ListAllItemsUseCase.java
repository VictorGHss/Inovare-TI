package br.dev.ctrls.inovareti.domain.inventory.usecase;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: lista todos os itens de inventário com sua categoria.
 * Usa {@code findAllWithCategory()} via JOIN FETCH para evitar o problema N+1.
 */
@Component
@RequiredArgsConstructor
public class ListAllItemsUseCase {

    private final ItemRepository itemRepository;

    /**
     * Retorna todos os itens de inventário cadastrados, com a categoria já carregada.
     *
     * @return lista de DTOs com os dados públicos dos itens
     */
    @Transactional(readOnly = true)
    public List<ItemResponseDTO> execute() {
        return itemRepository.findAllWithCategory()
                .stream()
                .map(ItemResponseDTO::from)
                .toList();
    }
}
