package br.dev.ctrls.inovareti.modules.vault.domain.port.output;
import br.dev.ctrls.inovareti.modules.vault.domain.port.output.VaultItemRepositoryPort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItem;

public interface VaultItemRepositoryPort {
    VaultItem save(VaultItem entity);
    Optional<VaultItem> findById(UUID id);
    List<VaultItem> findAll();
    void deleteById(UUID id);
    boolean existsById(UUID id);
    // Add custom methods manually if needed

    java.util.List<VaultItem> findVisibleItems(java.util.UUID userId, boolean isAdmin);
    void delete(VaultItem item);
}
