package br.dev.ctrls.inovareti.modules.asset.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetCategoryRepositoryPort;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetRequestDTO;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Observed
public class AssetService {

    private final AssetRepositoryPort assetRepository;
    private final AssetCategoryRepositoryPort assetCategoryRepository;
    private final UserRepositoryPort userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public List<Asset> createAssets(AssetRequestDTO request) {
        // Valida e busca os usuÃ¡rios associados, se informados no payload
        Set<User> usuarios = new HashSet<>();
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            for (java.util.UUID uid : request.userIds()) {
                User u = userRepository.findById(uid)
                        .orElseThrow(() -> new NotFoundException("UsuÃ¡rio nÃ£o encontrado com id: " + uid));
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
                throw new BadRequestException("CÃ³digo de patrimÃ´nio jÃ¡ existe: " + patrimonyCode);
            }

            // Popula a coleÃ§Ã£o de usuÃ¡rios com os usuÃ¡rios associados
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

    @Transactional
    public Asset updateAsset(java.util.UUID id, AssetRequestDTO request) {
        AssetCategory category = resolveCategory(request.categoryId());

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ativo nÃ£o encontrado com id: " + id));

        // Atualiza a coleÃ§Ã£o de usuÃ¡rios: se userIds foi fornecido, substitui pelos novos usuÃ¡rios;
        // caso contrÃ¡rio, mantÃ©m a coleÃ§Ã£o inalterada.
        if (request.userIds() != null) {
            if (asset.getUsers() == null) {
                asset.setUsers(new java.util.HashSet<>());
            }
            asset.getUsers().clear();
            for (java.util.UUID uid : request.userIds()) {
                User novoUsuario = userRepository.findById(uid)
                        .orElseThrow(() -> new NotFoundException("UsuÃ¡rio nÃ£o encontrado com id: " + uid));
                asset.getUsers().add(novoUsuario);
            }
        }

        asset.setName(request.name().trim());
        asset.setPatrimonyCode(request.patrimonyCode().trim());
        asset.setCategory(category);
        asset.setSpecifications(request.specifications());

        return assetRepository.save(asset);
    }

    public AssetCategory resolveCategory(java.util.UUID categoryId) {
        if (categoryId == null) {
            return null;
        }

        return assetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Categoria de ativo nÃ£o encontrada com id: " + categoryId));
    }
}


