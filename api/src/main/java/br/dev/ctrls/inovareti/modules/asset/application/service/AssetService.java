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
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditAction;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditEvent;
import br.dev.ctrls.inovareti.modules.audit.application.service.AuditLogService;
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

        // ─── FASE 1: Geração e validação de todos os códigos ANTES de qualquer insert ───
        // Separar a validação da persistência evita dois problemas:
        //   1. Bug de consistência: lançar exceção após já ter inserido os primeiros registros
        //      da lista, deixando a transação em estado parcialmente comprometido.
        //   2. Bug de UX: o usuário só descobria o conflito do código '-1' e precisava
        //      reenviar a requisição para descobrir que '-2' e '-3' também conflitam.
        // Com a pré-validação em lote, todos os conflitos são reportados de uma vez.
        List<String> codigosConflitantes = new ArrayList<>();
        List<String> codigosParaCriar = new ArrayList<>();

        for (int index = 1; index <= quantity; index++) {
            String patrimonyCode = quantity > 1 ? basePatrimonyCode + "-" + index : basePatrimonyCode;
            if (assetRepository.existsByPatrimonyCode(patrimonyCode)) {
                codigosConflitantes.add(patrimonyCode);
            } else {
                codigosParaCriar.add(patrimonyCode);
            }
        }

        // Se qualquer código do lote já existir, rejeita TODA a operação antes de inserir qualquer registro
        if (!codigosConflitantes.isEmpty()) {
            String listaConflitos = String.join(", ", codigosConflitantes);
            throw new BadRequestException(
                "Código(s) de patrimônio já existente(s) no sistema: " + listaConflitos +
                ". Nenhum ativo do lote foi criado. Corrija os códigos e tente novamente."
            );
        }

        // ─── FASE 2: Persistência — somente executada se não houver nenhum conflito ───
        List<Asset> createdAssets = new ArrayList<>();

        for (String patrimonyCode : codigosParaCriar) {
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

    @Transactional
    public Asset updateAsset(java.util.UUID id, AssetRequestDTO request) {
        AssetCategory category = resolveCategory(request.categoryId());

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ativo não encontrado com id: " + id));

        // Atualiza a coleção de usuários: se userIds foi fornecido, substitui pelos novos usuários;
        // caso contrário, mantém a coleção inalterada.
        if (request.userIds() != null) {
            if (asset.getUsers() == null) {
                asset.setUsers(new java.util.HashSet<>());
            }
            asset.getUsers().clear();
            for (java.util.UUID uid : request.userIds()) {
                User novoUsuario = userRepository.findById(uid)
                        .orElseThrow(() -> new NotFoundException("Usuário não encontrado com id: " + uid));
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
                .orElseThrow(() -> new NotFoundException("Categoria de ativo não encontrada com id: " + categoryId));
    }
}


