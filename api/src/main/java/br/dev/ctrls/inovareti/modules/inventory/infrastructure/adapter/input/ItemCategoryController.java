package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.input;
import io.micrometer.observation.annotation.Observed;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;

import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemCategoryRepositoryPort;


import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemCategoryRequestDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemCategoryResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.CreateItemCategoryUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.ListAllItemCategoriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de categorias de item de inventário.
 * Base path: /api/item-categories
 */
@RestController
@RequestMapping("/item-categories")
@RequiredArgsConstructor
@Observed
public class ItemCategoryController {

    private final CreateItemCategoryUseCase createItemCategoryUseCase;
    private final ListAllItemCategoriesUseCase listAllItemCategoriesUseCase;
    private final ItemCategoryRepositoryPort itemCategoryRepository;
    private final ItemRepositoryPort itemRepository;

    /**
     * Cria uma nova categoria de item de inventário.
     * Retorna 201 Created com a categoria criada no corpo da resposta.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ItemCategoryResponseDTO> create(
            @Valid @RequestBody ItemCategoryRequestDTO request) {
        ItemCategoryRequestDTO normalizedRequest = new ItemCategoryRequestDTO(
            request.name(),
            request.isConsumable() != null ? request.isConsumable() : Boolean.TRUE
        );

        ItemCategoryResponseDTO response = createItemCategoryUseCase.execute(normalizedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todas as categorias de item cadastradas.
     * Retorna 200 OK com a lista.
     * Todos os usuários autenticados podem ler (necessário para formulários de chamados).
     */
    @GetMapping
    public ResponseEntity<List<ItemCategoryResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllItemCategoriesUseCase.execute());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return itemCategoryRepository.findById(id).map(existing -> {
            long linked = itemRepository.countByItemCategory_Id(id);
            if (linked > 0) {
                throw new ConflictException("Não é possível excluir esta categoria: existem itens vinculados a ela.");
            }
            itemCategoryRepository.deleteById(id);
            return ResponseEntity.noContent().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}


