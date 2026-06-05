package br.dev.ctrls.inovareti.modules.asset.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;



public interface AssetCategoryRepositoryPort {
    AssetCategory save(AssetCategory entity);
    Optional<AssetCategory> findById(UUID id);
    List<AssetCategory> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    boolean existsByName(String name);

    java.util.Optional<br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory> findByName(String name);
}
