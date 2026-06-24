package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output.jpa.repository.AssetMaintenanceJpaRepository;

@Component
@RequiredArgsConstructor
public class AssetMaintenanceRepositoryAdapter implements AssetMaintenanceRepositoryPort {

    private final AssetMaintenanceJpaRepository repository;

    @Override
    public AssetMaintenance save(AssetMaintenance entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<AssetMaintenance> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<AssetMaintenance> findAll() {
        return repository.findAll();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
    
    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> findByCreatedAtBetweenAndTypeOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end, br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance.MaintenanceType type) {
        return repository.findByCreatedAtBetweenAndTypeOrderByCreatedAtDesc(start, end, type);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance> findByAssetIdOrderByMaintenanceDateDesc(java.util.UUID assetId) {
        return repository.findByAssetIdOrderByMaintenanceDateDesc(assetId);
    }

    @Override
    public java.util.List<Object[]> consolidateMaintenanceByPeriod(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return repository.consolidateMaintenanceByPeriod(start, end);
    }

}
