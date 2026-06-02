package br.dev.ctrls.inovareti.modules.vault.application.dto;

import java.util.UUID;

public record VaultSecretResponseDTO(
        UUID itemId,
        String secretContent
) {
}