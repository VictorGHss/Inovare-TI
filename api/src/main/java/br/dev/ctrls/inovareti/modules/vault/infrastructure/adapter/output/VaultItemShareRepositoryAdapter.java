package br.dev.ctrls.inovareti.modules.vault.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItemShare;
import br.dev.ctrls.inovareti.modules.vault.domain.port.output.VaultItemShareRepositoryPort;
import br.dev.ctrls.inovareti.modules.vault.infrastructure.adapter.output.jpa.repository.VaultItemShareJpaRepository;

@Component
@RequiredArgsConstructor
public class VaultItemShareRepositoryAdapter implements VaultItemShareRepositoryPort {

    private final VaultItemShareJpaRepository repository;

    @Override
    public VaultItemShare save(VaultItemShare entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<VaultItemShare> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<VaultItemShare> findAll() {
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
    public void deleteByVaultItemId(java.util.UUID itemId) {
        repository.deleteByVaultItemId(itemId);
    }


    @Override
    public java.util.List<VaultItemShare> saveAll(java.util.List<br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItemShare> shares) {
        return repository.saveAll(shares);
    }

    @Override
    public boolean existsByVaultItemIdAndSharedWithUserId(java.util.UUID itemId, java.util.UUID userId) {
        return repository.existsByVaultItemIdAndSharedWithUserId(itemId, userId);
    }

}
