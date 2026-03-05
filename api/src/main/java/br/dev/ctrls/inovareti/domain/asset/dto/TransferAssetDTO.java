package br.dev.ctrls.inovareti.domain.asset.dto;

import java.util.UUID;

public record TransferAssetDTO(
        UUID newUserId,  // null = devolver ao estoque
        String reason
) {}
