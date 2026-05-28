package br.dev.ctrls.inovareti.domain.asset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public List<Asset> createAssets(AssetRequestDTO request) {
        // Valida e busca os usuários associados, se informados no payload
        Set<User> usuarios = new HashSet<>();
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            for (java.util.UUID uid : request.userIds()) {
                User u = userRepository.findById(uid)
                        .orElseThrow(() -> new NotFoundException("Usuário não encontrado com id: " + uid));
                usuarios.add(u);
            }
        }

        AssetCategory category = resolveCategory(request.categoryId());
        Integer requestQuantity = request.quantity();
        int quantity = requestQuantity != null && requestQuantity > 0 ? requestQuantity : 1;

        String basePatrimonyCode = request.patrimonyCode().trim();
        List<Asset> createdAssets = new ArrayList<>();

        for (int index = 1; index <= quantity; index++) {
            String patrimonyCode = quantity > 1 ? basePatrimonyCode + "-" + index : basePatrimonyCode;

            if (assetRepository.existsByPatrimonyCode(patrimonyCode)) {
                throw new BadRequestException("Código de patrimônio já existe: " + patrimonyCode);
            }

            // Popula a coleção de usuários com os usuários associados
            Set<User> usuariosParaAtivo = new HashSet<>(usuarios);

            Asset asset = Asset.builder()
                    .users(usuariosParaAtivo)
                    .name(request.name().trim())
                    .patrimonyCode(patrimonyCode)
                    .category(category)
                    .specifications(request.specifications())
                    .build();

            Asset savedAsset = assetRepository.save(asset);
            auditLogService.publish(AuditEvent.of(AuditAction.ASSET_CREATE)
                        .resourceType("Asset")
                        .resourceId(savedAsset.getId())
                        .details("{\"patrimonyCode\": \"" + savedAsset.getPatrimonyCode()
                            + "\", \"hasInvoice\": false}")
                        .build());

            createdAssets.add(savedAsset);
        }

        return createdAssets;
    }

    public AssetCategory resolveCategory(java.util.UUID categoryId) {
        if (categoryId == null) {
            return null;
        }

        return assetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Categoria de ativo não encontrada com id: " + categoryId));
    }
}
