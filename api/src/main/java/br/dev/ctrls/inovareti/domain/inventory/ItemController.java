package br.dev.ctrls.inovareti.domain.inventory;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.inventory.dto.ItemRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.ItemResponseDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.StockBatchRequestDTO;
import br.dev.ctrls.inovareti.domain.inventory.dto.StockBatchResponseDTO;
import br.dev.ctrls.inovareti.domain.inventory.usecase.CreateItemUseCase;
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
    private final RegisterStockBatchUseCase registerStockBatchUseCase;

    /**
     * Cria um novo item de inventário com estoque inicial zero.
     * Retorna 201 Created com os dados do item.
     */
    @PostMapping
    public ResponseEntity<ItemResponseDTO> create(@Valid @RequestBody ItemRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createItemUseCase.execute(request));
    }

    /**
     * Registra um lote de entrada de estoque para o item informado.
     * Atualiza o currentStock do item atomicamente.
     * Retorna 201 Created com os dados do lote.
     */
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
