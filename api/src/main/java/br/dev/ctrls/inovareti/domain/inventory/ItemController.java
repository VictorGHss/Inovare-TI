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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
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
import br.dev.ctrls.inovareti.domain.shared.FileStorageService;
import br.dev.ctrls.inovareti.domain.shared.InvoiceFileMetadata;
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
    private final StockBatchRepository stockBatchRepository;
    private final FileStorageService fileStorageService;

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
                request.unitPrice(),
                request.brand(),
                request.supplier(),
                request.purchaseReason()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registerStockBatchUseCase.execute(consistentRequest));
    }

    /**
     * Upload de nota fiscal (PDF ou imagem) para um lote de estoque.
     * O arquivo é salvo em disco e os metadados são armazenados na entidade StockBatch.
     *
     * POST /api/items/{itemId}/batches/{batchId}/invoice
     * Content-Type: multipart/form-data
     * Form parameter: file (MultipartFile)
     *
     * @param itemId  UUID do Item
     * @param batchId UUID do StockBatch
     * @param file    Arquivo PDF ou Imagem (máx 5MB)
     * @return        Lote atualizado com metadados da NF
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{itemId}/batches/{batchId}/invoice")
    public ResponseEntity<BatchResponseDTO> uploadBatchInvoice(
            @PathVariable UUID itemId,
            @PathVariable UUID batchId,
            @RequestParam("file") MultipartFile file) throws BadRequestException {

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Stock batch not found with id: " + batchId));

        // Validar que o lote pertence ao item informado
        if (!batch.getItem().getId().equals(itemId)) {
            throw new BadRequestException("Stock batch does not belong to the specified item.");
        }

        // Se já existe um arquivo anterior, remove-o do disco
        if (batch.getInvoiceFilePath() != null) {
            fileStorageService.deleteInvoiceFile(batch.getInvoiceFilePath());
        }

        // Salva o novo arquivo
        InvoiceFileMetadata metadata = fileStorageService.saveInvoiceFile(file, batchId, "batch");

        // Atualiza a entidade com os metadados do arquivo
        batch.setInvoiceFileName(metadata.getFileName());
        batch.setInvoiceContentType(metadata.getContentType());
        batch.setInvoiceFilePath(metadata.getFilePath());

        StockBatch updatedBatch = stockBatchRepository.save(batch);
        return ResponseEntity.ok(BatchResponseDTO.from(updatedBatch));
    }

    /**
     * Download de nota fiscal (PDF ou imagem) de um lote de estoque.
     *
     * GET /api/items/{itemId}/batches/{batchId}/invoice
     *
     * @param itemId  UUID do Item
     * @param batchId UUID do StockBatch
     * @return        Arquivo binário com headers apropriados (Content-Disposition, Content-Type)
     */
    @GetMapping("/{itemId}/batches/{batchId}/invoice")
    public ResponseEntity<byte[]> downloadBatchInvoice(
            @PathVariable UUID itemId,
            @PathVariable UUID batchId) {

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Stock batch not found with id: " + batchId));

        // Validar que o lote pertence ao item informado
        if (!batch.getItem().getId().equals(itemId)) {
            throw new BadRequestException("Stock batch does not belong to the specified item.");
        }

        if (batch.getInvoiceFilePath() == null || batch.getInvoiceFilePath().isBlank()) {
            throw new NotFoundException("Nenhuma nota fiscal anexada a este lote.");
        }

        byte[] fileContent = fileStorageService.loadInvoiceFile(batch.getInvoiceFilePath());

        return ResponseEntity.ok()
                .header("Content-Type", batch.getInvoiceContentType())
                .header("Content-Disposition",
                        "inline; filename=\"" + batch.getInvoiceFileName() + "\"")
                .body(fileContent);
    }
}
