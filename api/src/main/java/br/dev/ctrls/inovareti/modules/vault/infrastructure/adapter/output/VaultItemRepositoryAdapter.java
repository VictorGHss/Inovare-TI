package br.dev.ctrls.inovareti.modules.vault.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItem;
import br.dev.ctrls.inovareti.modules.vault.domain.port.output.VaultItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.vault.infrastructure.adapter.output.jpa.repository.VaultItemJpaRepository;

@Component
@RequiredArgsConstructor
public class VaultItemRepositoryAdapter implements VaultItemRepositoryPort {

    private final VaultItemJpaRepository repository;

    @Override
    public VaultItem save(VaultItem entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<VaultItem> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<VaultItem> findAll() {
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
    public java.util.List<VaultItem> findVisibleItems(java.util.UUID userId, boolean isAdmin) {
        return repository.findVisibleItems(userId, isAdmin);
    }

    @Override
    public void delete(VaultItem item) {
        repository.delete(item);
    }

}
