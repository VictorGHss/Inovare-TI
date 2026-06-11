package br.dev.ctrls.inovareti.modules.vault.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItemShare;


import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultItemShareJpaRepository extends JpaRepository<VaultItemShare, UUID> {

    boolean existsByVaultItemIdAndSharedWithUserId(UUID vaultItemId, UUID sharedWithUserId);

    void deleteByVaultItemId(UUID vaultItemId);
}