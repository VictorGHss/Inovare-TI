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
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetResponseDTO;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @GetMapping
    public ResponseEntity<List<AssetResponseDTO>> listAll() {
        List<AssetResponseDTO> response = assetRepository.findAll()
                .stream()
                .map(AssetResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
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
}
