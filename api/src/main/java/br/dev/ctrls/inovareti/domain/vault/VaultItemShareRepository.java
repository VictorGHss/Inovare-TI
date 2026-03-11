package br.dev.ctrls.inovareti.domain.vault;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VaultItemShareRepository extends JpaRepository<VaultItemShare, UUID> {

    boolean existsByVaultItemIdAndSharedWithUserId(UUID vaultItemId, UUID sharedWithUserId);
}