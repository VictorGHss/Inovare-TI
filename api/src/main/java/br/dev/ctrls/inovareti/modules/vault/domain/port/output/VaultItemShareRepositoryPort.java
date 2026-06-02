package br.dev.ctrls.inovareti.modules.vault.domain.port.output;
import br.dev.ctrls.inovareti.modules.vault.domain.port.output.VaultItemShareRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItemShare;

public interface VaultItemShareRepositoryPort {
    VaultItemShare save(VaultItemShare entity);
    Optional<VaultItemShare> findById(UUID id);
    List<VaultItemShare> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    void deleteByVaultItemId(java.util.UUID itemId);

    java.util.List<VaultItemShare> saveAll(java.util.List<br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItemShare> shares);
    boolean existsByVaultItemIdAndSharedWithUserId(java.util.UUID itemId, java.util.UUID userId);
}
