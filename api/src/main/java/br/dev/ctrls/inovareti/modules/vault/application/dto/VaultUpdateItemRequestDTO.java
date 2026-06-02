package br.dev.ctrls.inovareti.modules.vault.application.dto;

import java.util.List;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultItemType;
import br.dev.ctrls.inovareti.modules.vault.domain.model.VaultSharingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VaultUpdateItemRequestDTO(
        @NotBlank(message = "O título do item é obrigatório.")
        String title,

        String description,

        @NotNull(message = "O tipo do item é obrigatório.")
        VaultItemType itemType,

        String secretContent,

        @NotNull(message = "O tipo de compartilhamento é obrigatório.")
        VaultSharingType sharingType,

        List<UUID> sharedWithUserIds
) {
}
