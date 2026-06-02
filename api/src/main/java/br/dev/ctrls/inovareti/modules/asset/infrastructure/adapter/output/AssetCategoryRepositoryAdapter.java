package br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.infrastructure.adapter.output.jpa.repository.AssetCategoryJpaRepository;

@Component
@RequiredArgsConstructor
public class AssetCategoryRepositoryAdapter implements AssetCategoryRepositoryPort {

    private final AssetCategoryJpaRepository repository;

    @Override
    public AssetCategory save(AssetCategory entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<AssetCategory> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<AssetCategory> findAll() {
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
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }


    @Override
    public java.util.Optional<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory> findByName(String name) {
        return repository.findByName(name);
    }

}
