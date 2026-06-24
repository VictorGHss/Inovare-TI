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

    List<AssetMaintenance> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);

    @org.springframework.data.jpa.repository.Query("SELECT a.patrimonyCode, a.name, COUNT(am), COALESCE(SUM(am.cost), 0) " +
           "FROM AssetMaintenance am JOIN am.asset a " +
           "WHERE am.createdAt BETWEEN :start AND :end " +
           "GROUP BY a.patrimonyCode, a.name")
    List<Object[]> consolidateMaintenanceByPeriod(
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);
}
