package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output.jpa.repository;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMaintenanceJpaRepository extends JpaRepository<AssetMaintenance, UUID> {
    List<AssetMaintenance> findByAssetIdOrderByMaintenanceDateDesc(UUID assetId);

    List<AssetMaintenance> findByDescriptionContainingAndType(String descriptionSubstring, AssetMaintenance.MaintenanceType type);

    List<AssetMaintenance> findByDescriptionContainingIgnoreCase(String descriptionSubstring);

    List<AssetMaintenance> findByCreatedAtBetweenAndTypeOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end, AssetMaintenance.MaintenanceType type);
}
