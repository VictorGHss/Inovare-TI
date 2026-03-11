package br.dev.ctrls.inovareti.domain.vault.dto;

import java.util.UUID;

public record VaultSecretResponseDTO(
        UUID itemId,
        String secretContent
) {
}