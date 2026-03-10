package br.dev.ctrls.inovareti.domain.asset;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<Asset> createAssets(AssetRequestDTO request) {
        if (!userRepository.existsById(request.userId())) {
            throw new NotFoundException("User not found with id: " + request.userId());
        }

        AssetCategory category = resolveCategory(request.categoryId());
        Integer requestQuantity = request.quantity();
        int quantity = requestQuantity != null && requestQuantity > 0 ? requestQuantity : 1;

        String basePatrimonyCode = request.patrimonyCode().trim();
        List<Asset> createdAssets = new ArrayList<>();

        for (int index = 1; index <= quantity; index++) {
            String patrimonyCode = quantity > 1 ? basePatrimonyCode + "-" + index : basePatrimonyCode;

            if (assetRepository.existsByPatrimonyCode(patrimonyCode)) {
                throw new BadRequestException("Patrimony code already exists: " + patrimonyCode);
            }

            Asset asset = Asset.builder()
                    .userId(request.userId())
                    .name(request.name().trim())
                    .patrimonyCode(patrimonyCode)
                    .category(category)
                    .specifications(request.specifications())
                    .build();

            createdAssets.add(assetRepository.save(asset));
        }

        return createdAssets;
    }

    private AssetCategory resolveCategory(java.util.UUID categoryId) {
        if (categoryId == null) {
            return null;
        }

        return assetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Asset category not found with id: " + categoryId));
    }
}
