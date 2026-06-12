package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;



import br.dev.ctrls.inovareti.modules.asset.application.service.AssetService;
import br.dev.ctrls.inovareti.modules.asset.application.service.AssetQueryService;
import br.dev.ctrls.inovareti.modules.asset.application.service.AssetMaintenanceService;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;


import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditAction;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditEvent;
import br.dev.ctrls.inovareti.modules.audit.application.service.AuditLogService;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetMaintenanceRequestDTO;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetMaintenanceResponseDTO;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetRequestDTO;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetResponseDTO;
import br.dev.ctrls.inovareti.modules.asset.application.dto.TransferAssetDTO;
import br.dev.ctrls.inovareti.infrastructure.shared.storage.FileStorageService;
import br.dev.ctrls.inovareti.infrastructure.shared.storage.InvoiceFileMetadata;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/assets")
@Observed
public class AssetController {

    private final AssetRepositoryPort assetRepository;
    private final UserRepositoryPort userRepository;
    private final AssetService assetService;
    private final AssetQueryService assetQueryService;
    private final FileStorageService fileStorageService;
    private final AssetMaintenanceService maintenanceService;
    private final AuditLogService auditLogService;

    public AssetController(
            AssetRepositoryPort assetRepository,
            UserRepositoryPort userRepository,
            AssetService assetService,
            AssetQueryService assetQueryService,
            FileStorageService fileStorageService,
            AssetMaintenanceService maintenanceService,
            AuditLogService auditLogService
    ) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.assetService = assetService;
        this.assetQueryService = assetQueryService;
        this.fileStorageService = fileStorageService;
        this.maintenanceService = maintenanceService;
        this.auditLogService = auditLogService;
    }


    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<AssetResponseDTO>> listAll(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "0") int page
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, 15);
        org.springframework.data.domain.Page<AssetResponseDTO> response = assetQueryService.listAssets(
                categoryId,
                status,
                sortBy,
                pageable,
                assetRepository
        );
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    @GetMapping("/{id}")
    public ResponseEntity<AssetResponseDTO> findById(@PathVariable UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));
        return ResponseEntity.ok(assetQueryService.toResponseDTO(asset));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AssetResponseDTO>> findByUser(@PathVariable UUID userId) {
        List<AssetResponseDTO> response = assetRepository.findByUsersId(userId)
                .stream()
                .map(assetQueryService::toResponseDTO)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<AssetResponseDTO> create(@Valid @RequestBody AssetRequestDTO request) {
        Asset savedAsset = assetService.createAssets(request).get(0);
        return ResponseEntity.status(HttpStatus.CREATED).body(assetQueryService.toResponseDTO(savedAsset));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PatchMapping("/{id}")
    public ResponseEntity<AssetResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody AssetRequestDTO request) {
        Asset savedAsset = assetService.updateAsset(id, request);

        auditLogService.publish(AuditEvent.of(AuditAction.ASSET_EDIT)
                .userId(getAuthenticatedUser().getId())
                .resourceType("Asset")
                .resourceId(savedAsset.getId())
                .details("{\"patrimonyCode\": \"" + savedAsset.getPatrimonyCode() + "\"}")
                .build());
        return ResponseEntity.ok(assetQueryService.toResponseDTO(savedAsset));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));

        assetRepository.delete(asset);
        return ResponseEntity.noContent().build();
    }

    /**
     * Upload de nota fiscal (PDF ou imagem) para um ativo.
     * O arquivo é salvo em disco e os metadados são armazenados na entidade Asset.
     *
     * POST /api/assets/{id}/invoice
     * Content-Type: multipart/form-data
     * Form parameter: file (MultipartFile)
     *
     * @param id   UUID do Asset
     * @param file Arquivo PDF ou Imagem (máx 5MB)
     * @return     Ativo atualizado com metadados da NF
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/invoice")
    public ResponseEntity<AssetResponseDTO> uploadInvoice(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws BadRequestException {

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));

        // Se já existe um arquivo anterior, remove-o do disco
        if (asset.getInvoiceFilePath() != null) {
            fileStorageService.deleteInvoiceFile(asset.getInvoiceFilePath());
        }

        // Salva o novo arquivo
        InvoiceFileMetadata metadata = fileStorageService.saveInvoiceFile(file, id, "asset");

        // Atualiza a entidade com os metadados do arquivo
        asset.setInvoiceFileName(metadata.getFileName());
        asset.setInvoiceContentType(metadata.getContentType());
        asset.setInvoiceFilePath(metadata.getFilePath());

        Asset updatedAsset = assetRepository.save(asset);
        auditLogService.publish(AuditEvent.of(AuditAction.ASSET_INVOICE_ATTACH)
                .resourceType("Asset")
                .resourceId(updatedAsset.getId())
                .details("{\"invoiceFileName\": \"" + metadata.getFileName() + "\"}")
                .build());
        return ResponseEntity.ok(assetQueryService.toResponseDTO(updatedAsset));
    }

    /**
     * Download de nota fiscal (PDF ou imagem) de um ativo.
     *
     * GET /api/assets/{id}/invoice
     *
     * @param id UUID do Asset
     * @return   Arquivo binário com headers apropriados (Content-Disposition, Content-Type)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @GetMapping("/{id}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));

        if (asset.getInvoiceFilePath() == null || asset.getInvoiceFilePath().isBlank()) {
            throw new NotFoundException("Nenhuma nota fiscal anexada a este ativo.");
        }

        byte[] fileContent = fileStorageService.loadInvoiceFile(asset.getInvoiceFilePath());

        return ResponseEntity.ok()
                .header("Content-Type", asset.getInvoiceContentType())
                .header("Content-Disposition",
                        "inline; filename=\"" + asset.getInvoiceFileName() + "\"")
                .body(fileContent);
    }

    private User getAuthenticatedUser() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    /**
     * Registra uma nova manutenção para um ativo.
     *
     * POST /api/assets/{id}/maintenances
     * Body: AssetMaintenanceRequestDTO
     *
     * O usuário logado é automaticamente definido como técnico responsável.
     *
     * @param id      UUID do Asset
     * @param request Dados da manutenção (data, tipo, custo, descrição)
     * @return        Manutenção criada
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/maintenances")
    public ResponseEntity<AssetMaintenanceResponseDTO> createMaintenance(
            @PathVariable UUID id,
            @Valid @RequestBody AssetMaintenanceRequestDTO request) {

        // Obtém o usuário logado do SecurityContextHolder
        String userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        User technician = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        AssetMaintenanceResponseDTO response = maintenanceService.create(id, request, technician);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todas as manutenções de um ativo, ordenadas por data DESC.
     *
     * GET /api/assets/{id}/maintenances
     *
     * @param id UUID do Asset
     * @return   Lista de manutenções formatadas
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    @GetMapping("/{id}/maintenances")
    public ResponseEntity<List<AssetMaintenanceResponseDTO>> listMaintenances(@PathVariable UUID id) {
        List<AssetMaintenanceResponseDTO> response = maintenanceService.getByAssetId(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Transfere um ativo para um novo usuário ou o devolve ao estoque da TI.
     *
     * PATCH /api/assets/{id}/transfer
     * Body: TransferAssetDTO { newUserId (nullable), reason }
     *
     * Se newUserId for null, o ativo é desvinculado e retornado ao estoque.
     * Cria automaticamente um log de transferência no histórico de manutenções.
     *
     * @param id   UUID do Asset
     * @param request Dados da transferência (novo usuário e motivo)
     * @return    Ativo atualizado
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PatchMapping("/{id}/transfer")
    public ResponseEntity<AssetResponseDTO> transferAsset(
            @PathVariable UUID id,
            @Valid @RequestBody TransferAssetDTO request) {

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));

        // Valida o novo usuário se foi fornecido
        User newUser = null;
        if (request.newUserId() != null) {
            newUser = userRepository.findById(request.newUserId())
                    .orElseThrow(() -> new NotFoundException("Usuário não encontrado com id: " + request.newUserId()));
        }

        // Captura o primeiro usuário atual para trilha de auditoria (modelo N:N: primeiro da coleção)
        User oldUser = (asset.getUsers() != null && !asset.getUsers().isEmpty())
                ? asset.getUsers().iterator().next()
                : null;

        // Obtém o usuário logado (técnico que realiza a transferência)
        String userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        User technician = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado com id: " + userId));

        // Substitui a coleção de usuários: se newUserId for null, devolve ao estoque (coleção vazia)
        if (asset.getUsers() == null) {
            asset.setUsers(new java.util.HashSet<>());
        }
        asset.getUsers().clear();
        if (newUser != null) {
            asset.getUsers().add(newUser);
        }
        Asset updatedAsset = assetRepository.save(asset);

        // Cria o log de transferência
        maintenanceService.createTransferLog(updatedAsset, oldUser, newUser, request.reason(), technician);

        return ResponseEntity.ok(assetQueryService.toResponseDTO(updatedAsset));
    }
}


