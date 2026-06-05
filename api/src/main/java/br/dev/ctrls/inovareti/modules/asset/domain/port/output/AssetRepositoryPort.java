package br.dev.ctrls.inovareti.modules.asset.domain.port.output;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;



public interface AssetRepositoryPort {
    Asset save(Asset entity);
    Optional<Asset> findById(UUID id);
    List<Asset> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    boolean existsByPatrimonyCode(String code);
    java.util.Optional<Asset> findByPatrimonyCode(String code);

    void delete(br.dev.ctrls.inovareti.modules.asset.domain.model.Asset entity);

    long countInStock();
    long countByCategory_Id(java.util.UUID id);
    java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.Asset> findByUsersId(java.util.UUID userId);

    long countByUsersId(java.util.UUID userId);
    long count();
    long countInUse();
    java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.Asset> findWithFiltersOrderByMaintenanceCountDesc(java.util.UUID categoryId, String searchTerm);
    java.util.List<br.dev.ctrls.inovareti.modules.asset.domain.model.Asset> findWithFiltersOrderByCreatedAtDesc(java.util.UUID categoryId, String searchTerm);
}
