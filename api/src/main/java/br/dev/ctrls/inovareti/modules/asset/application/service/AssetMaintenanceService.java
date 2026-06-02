package br.dev.ctrls.inovareti.modules.asset.application.service;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;

import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetMaintenanceRequestDTO;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetMaintenanceResponseDTO;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetMaintenanceService {

    private final AssetMaintenanceRepositoryPort maintenanceRepository;
    private final AssetRepositoryPort assetRepository;

    public AssetMaintenanceResponseDTO create(UUID assetId, AssetMaintenanceRequestDTO request, User technician) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset not found with id: " + assetId));

        AssetMaintenance maintenance = AssetMaintenance.builder()
                .asset(asset)
                .maintenanceDate(request.maintenanceDate())
                .type(request.type())
                .description(request.description())
                .cost(request.cost())
                .technician(technician)
                .build();

        AssetMaintenance savedMaintenance = maintenanceRepository.save(maintenance);
        return AssetMaintenanceResponseDTO.from(savedMaintenance);
    }

    public List<AssetMaintenanceResponseDTO> getByAssetId(UUID assetId) {
        // Valida se o ativo existe
        if (!assetRepository.existsById(assetId)) {
            throw new NotFoundException("Asset not found with id: " + assetId);
        }

        return maintenanceRepository.findByAssetIdOrderByMaintenanceDateDesc(assetId)
                .stream()
                .map(AssetMaintenanceResponseDTO::from)
                .toList();
    }

    public void createTransferLog(Asset asset, User oldUser, User newUser, String reason, User technician) {
        String oldUserName = oldUser != null ? oldUser.getName() : "Estoque da TI";
        String newUserName = newUser != null ? newUser.getName() : "Estoque da TI";

        String description = newUser != null
                ? String.format("Ativo transferido de %s para %s. Motivo: %s", oldUserName, newUserName, reason)
                : String.format("Ativo desvinculado de %s e retornado ao estoque. Motivo: %s", oldUserName, reason);

        AssetMaintenance maintenance = AssetMaintenance.builder()
                .asset(asset)
                .maintenanceDate(LocalDate.now())
                .type(AssetMaintenance.MaintenanceType.TRANSFER)
                .description(description)
                .cost(BigDecimal.ZERO)
                .technician(technician)
                .build();

        maintenanceRepository.save(maintenance);
    }
}
