package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.inventory.dto.ItemCategoryRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemCategoryResponseDTO;
import br.dev.ctrls.inovareti.domain.inventory.usecase.CreateItemCategoryUseCase;
import br.dev.ctrls.inovareti.domain.inventory.usecase.ListAllItemCategoriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de categorias de item de inventário.
 * Base path: /api/item-categories
 */
@RestController
@RequestMapping("/api/item-categories")
@RequiredArgsConstructor
public class ItemCategoryController {

    private final CreateItemCategoryUseCase createItemCategoryUseCase;
    private final ListAllItemCategoriesUseCase listAllItemCategoriesUseCase;

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
}
