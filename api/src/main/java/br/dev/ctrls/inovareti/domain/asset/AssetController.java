package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetResponseDTO;
import br.dev.ctrls.inovareti.domain.shared.FileStorageService;
import br.dev.ctrls.inovareti.domain.shared.InvoiceFileMetadata;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @GetMapping
    public ResponseEntity<List<AssetResponseDTO>> listAll() {
        List<AssetResponseDTO> response = assetRepository.findAll()
                .stream()
                .map(AssetResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    @GetMapping("/{id}")
    public ResponseEntity<AssetResponseDTO> findById(@PathVariable UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));
        return ResponseEntity.ok(AssetResponseDTO.from(asset));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AssetResponseDTO>> findByUser(@PathVariable UUID userId) {
        List<AssetResponseDTO> response = assetRepository.findByUserId(userId)
                .stream()
                .map(AssetResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<AssetResponseDTO> create(@Valid @RequestBody AssetRequestDTO request) {
        if (!userRepository.existsById(request.userId())) {
            throw new NotFoundException("User not found with id: " + request.userId());
        }

        Asset asset = Asset.builder()
                .userId(request.userId())
                .name(request.name().trim())
                .patrimonyCode(request.patrimonyCode().trim())
                .specifications(request.specifications())
                .build();

        Asset savedAsset = assetRepository.save(asset);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetResponseDTO.from(savedAsset));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PatchMapping("/{id}")
    public ResponseEntity<AssetResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody AssetRequestDTO request) {
        if (!userRepository.existsById(request.userId())) {
            throw new NotFoundException("User not found with id: " + request.userId());
        }

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + id));

        asset.setUserId(request.userId());
        asset.setName(request.name().trim());
        asset.setPatrimonyCode(request.patrimonyCode().trim());
        asset.setSpecifications(request.specifications());

        Asset savedAsset = assetRepository.save(asset);
        return ResponseEntity.ok(AssetResponseDTO.from(savedAsset));
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
        return ResponseEntity.ok(AssetResponseDTO.from(updatedAsset));
    }

    /**
     * Download de nota fiscal (PDF ou imagem) de um ativo.
     *
     * GET /api/assets/{id}/invoice
     *
     * @param id UUID do Asset
     * @return   Arquivo binário com headers apropriados (Content-Disposition, Content-Type)
     */
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
}
