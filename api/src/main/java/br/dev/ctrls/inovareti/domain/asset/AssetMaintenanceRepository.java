package br.dev.ctrls.inovareti.domain.asset;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, UUID> {
    List<AssetMaintenance> findByAssetIdOrderByMaintenanceDateDesc(UUID assetId);
}
