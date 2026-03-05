package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetMaintenanceRequestDTO;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetMaintenanceResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetMaintenanceService {

    private final AssetMaintenanceRepository maintenanceRepository;
    private final AssetRepository assetRepository;

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
}
