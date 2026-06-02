package br.dev.ctrls.inovareti.modules.asset.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;

public record AssetMaintenanceResponseDTO(
        UUID id,
        UUID assetId,
        LocalDate maintenanceDate,
        String type,
        String description,
        BigDecimal cost,
        String technicianName,
        String technicianEmail
) {
    public static AssetMaintenanceResponseDTO from(AssetMaintenance maintenance) {
        return new AssetMaintenanceResponseDTO(
                maintenance.getId(),
                maintenance.getAsset().getId(),
                maintenance.getMaintenanceDate(),
                formatType(maintenance.getType()),
                maintenance.getDescription(),
                maintenance.getCost(),
                maintenance.getTechnician().getName(),
                maintenance.getTechnician().getEmail()
        );
    }

    private static String formatType(AssetMaintenance.MaintenanceType type) {
        return switch (type) {
            case PREVENTIVE -> "Preventiva";
            case CORRECTIVE -> "Corretiva";
            case UPGRADE -> "Upgrade";
            case TRANSFER -> "Transferência";
        };
    }
}
