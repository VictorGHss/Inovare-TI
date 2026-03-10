package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetCategoryRequestDTO;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetCategoryResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/asset-categories")
@RequiredArgsConstructor
public class AssetCategoryController {

    private final AssetCategoryRepository assetCategoryRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<AssetCategoryResponseDTO> create(@Valid @RequestBody AssetCategoryRequestDTO request) {
        String normalizedName = request.name().trim();

        if (assetCategoryRepository.existsByName(normalizedName)) {
            throw new ConflictException("Já existe uma categoria de equipamento com o nome: " + normalizedName);
        }

        AssetCategory created = assetCategoryRepository.save(
                AssetCategory.builder()
                        .name(normalizedName)
                        .build()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(AssetCategoryResponseDTO.from(created));
    }

    @GetMapping
    public ResponseEntity<List<AssetCategoryResponseDTO>> listAll() {
        List<AssetCategoryResponseDTO> response = assetCategoryRepository.findAll()
                .stream()
                .map(AssetCategoryResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
