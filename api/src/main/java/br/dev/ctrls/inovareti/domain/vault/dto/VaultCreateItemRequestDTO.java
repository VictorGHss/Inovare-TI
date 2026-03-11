package br.dev.ctrls.inovareti.domain.vault.dto;

import java.util.List;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.vault.VaultItemType;
import br.dev.ctrls.inovareti.domain.vault.VaultSharingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VaultCreateItemRequestDTO(
        @NotBlank(message = "O título do item é obrigatório.")
        String title,

        String description,

        @NotNull(message = "O tipo do item é obrigatório.")
        VaultItemType itemType,

        String secretContent,

        String filePath,

        @NotNull(message = "O tipo de compartilhamento é obrigatório.")
        VaultSharingType sharingType,

        List<UUID> sharedWithUserIds
) {
}