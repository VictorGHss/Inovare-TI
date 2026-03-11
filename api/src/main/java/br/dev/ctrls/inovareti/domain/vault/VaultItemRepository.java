package br.dev.ctrls.inovareti.domain.vault;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {

    @Query("""
            SELECT DISTINCT item
            FROM VaultItem item
            LEFT JOIN VaultItemShare share ON share.vaultItem.id = item.id
            WHERE item.owner.id = :userId
               OR (item.sharingType = br.dev.ctrls.inovareti.domain.vault.VaultSharingType.ALL_TECH_ADMIN AND :isTechAdmin = true)
               OR (item.sharingType = br.dev.ctrls.inovareti.domain.vault.VaultSharingType.CUSTOM AND share.sharedWithUser.id = :userId)
            """)
    List<VaultItem> findVisibleItems(@Param("userId") UUID userId, @Param("isTechAdmin") boolean isTechAdmin);
}