package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.asset.dto.AssetCategoryResponseDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/asset-categories")
@RequiredArgsConstructor
public class AssetCategoryController {

    private final AssetCategoryRepository assetCategoryRepository;

    @GetMapping
    public ResponseEntity<List<AssetCategoryResponseDTO>> listAll() {
        List<AssetCategoryResponseDTO> response = assetCategoryRepository.findAll()
                .stream()
                .map(AssetCategoryResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
