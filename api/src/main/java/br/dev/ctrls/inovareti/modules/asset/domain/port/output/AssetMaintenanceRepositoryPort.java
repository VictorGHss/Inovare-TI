package br.dev.ctrls.inovareti.modules.asset.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;



public interface AssetMaintenanceRepositoryPort {
    AssetMaintenance save(AssetMaintenance entity);
    Optional<AssetMaintenance> findById(UUID id);
    List<AssetMaintenance> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> findByCreatedAtBetweenAndTypeOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end, br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance.MaintenanceType type);
    java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);
    java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> findByAssetIdOrderByMaintenanceDateDesc(java.util.UUID assetId);
    java.util.List<Object[]> consolidateMaintenanceByPeriod(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
