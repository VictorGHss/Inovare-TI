package br.dev.ctrls.inovareti.domain.vault.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.vault.VaultItem;
import br.dev.ctrls.inovareti.domain.vault.VaultItemType;
import br.dev.ctrls.inovareti.domain.vault.VaultSharingType;

public record VaultItemResponseDTO(
        UUID id,
        String title,
        String description,
        VaultItemType itemType,
        String filePath,
        UUID ownerId,
        VaultSharingType sharingType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VaultItemResponseDTO from(VaultItem item) {
        return new VaultItemResponseDTO(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getItemType(),
                item.getFilePath(),
                item.getOwner().getId(),
                item.getSharingType(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}