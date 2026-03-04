package br.dev.ctrls.inovareti.domain.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.inventory.dto.BatchResponseDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemResponseDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.StockBatchRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.StockBatchResponseDTO;
import br.dev.ctrls.inovareti.domain.inventory.usecase.CreateItemUseCase;
import br.dev.ctrls.inovareti.domain.inventory.usecase.FindItemByIdUseCase;
import br.dev.ctrls.inovareti.domain.inventory.usecase.ListAllItemsUseCase;
import br.dev.ctrls.inovareti.domain.inventory.usecase.ListItemBatchesUseCase;
import br.dev.ctrls.inovareti.domain.inventory.usecase.RegisterStockBatchUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de itens de inventário e seus lotes de estoque.
 * Base path: /api/items
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final CreateItemUseCase createItemUseCase;
    private final ListAllItemsUseCase listAllItemsUseCase;
    private final RegisterStockBatchUseCase registerStockBatchUseCase;
    private final FindItemByIdUseCase findItemByIdUseCase;
    private final ListItemBatchesUseCase listItemBatchesUseCase;

    /**
     * Retorna todos os itens de inventário, com categoria carregada via JOIN FETCH.
     * Retorna 200 OK com a lista (vazia se não houver itens).
     * Todos os usuários autenticados podem ler (necessário para formulários de chamados).
     */
    @GetMapping
    public ResponseEntity<List<ItemResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllItemsUseCase.execute());
    }

    /**
     * Busca um item de inventário específico por ID.
     * Retorna 200 OK com os dados do item ou 404 se não encontrado.
     * Todos os usuários autenticados podem ler (necessário para visualização em chamados).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ItemResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(findItemByIdUseCase.execute(id));
    }

    /**
     * Lista todos os lotes de estoque de um item específico.
     * Os lotes são retornados ordenados do mais recente para o mais antigo.
     * Retorna 200 OK com a lista (vazia se não houver lotes).
     * Todos os usuários autenticados podem ler informações de estoque.
     */
    @GetMapping("/{id}/batches")
    public ResponseEntity<List<BatchResponseDTO>> listBatches(@PathVariable UUID id) {
        return ResponseEntity.ok(listItemBatchesUseCase.execute(id));
    }

    /**
     * Cria um novo item de inventário com estoque inicial zero.
     * Retorna 201 Created com os dados do item.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<ItemResponseDTO> create(@Valid @RequestBody ItemRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createItemUseCase.execute(request));
    }

    /**
     * Registra um lote de entrada de estoque para o item informado.
     * Atualiza o currentStock do item atomicamente.
     * Retorna 201 Created com os dados do lote.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/batches")
    public ResponseEntity<StockBatchResponseDTO> registerBatch(
            @PathVariable UUID id,
            @Valid @RequestBody StockBatchRequestDTO request) {

        // Garante que o itemId do path e do body são consistentes
        StockBatchRequestDTO consistentRequest = new StockBatchRequestDTO(
                id,
                request.quantity(),
                request.unitPrice()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registerStockBatchUseCase.execute(consistentRequest));
    }
}
