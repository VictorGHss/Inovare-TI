package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output.jpa.repository.AssetJpaRepository;

@Component
@RequiredArgsConstructor
public class AssetRepositoryAdapter implements AssetRepositoryPort {

    private final AssetJpaRepository repository;

    @Override
    public Asset save(Asset entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<Asset> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Asset> findAll() {
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
    public boolean existsByPatrimonyCode(String code) {
        return repository.existsByPatrimonyCode(code);
    }

    @Override
    public java.util.Optional<Asset> findByPatrimonyCode(String code) {
        return repository.findByPatrimonyCode(code);
    }


    @Override
    public void delete(br.dev.ctrls.inovareti.modules.asset.domain.model.Asset entity) {
        repository.delete(entity);
    }


    @Override
    public long countInStock() {
        return repository.countInStock();
    }

    @Override
    public long countByCategory_Id(java.util.UUID id) {
        return repository.countByCategory_Id(id);
    }

    @Override
    public java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.Asset> findByUsersId(java.util.UUID userId) {
        return repository.findByUsersId(userId);
    }


    @Override
    public long countByUsersId(java.util.UUID userId) {
        return repository.countByUsersId(userId);
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public long countInUse() {
        return repository.countInUse();
    }

    @Override
    public org.springframework.data.domain.Page<br.dev.ctrls.inovareti.modules.asset.domain.model.Asset> findWithFiltersOrderByMaintenanceCountDesc(java.util.UUID categoryId, String searchTerm, org.springframework.data.domain.Pageable pageable) {
        return repository.findWithFiltersOrderByMaintenanceCountDesc(categoryId, searchTerm, pageable);
    }

    @Override
    public org.springframework.data.domain.Page<br.dev.ctrls.inovareti.modules.asset.domain.model.Asset> findWithFiltersOrderByCreatedAtDesc(java.util.UUID categoryId, String searchTerm, org.springframework.data.domain.Pageable pageable) {
        return repository.findWithFiltersOrderByCreatedAtDesc(categoryId, searchTerm, pageable);
    }

}
