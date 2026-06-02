package br.dev.ctrls.inovareti.modules.asset.application.dto;

import java.util.UUID;

public record TransferAssetDTO(
        UUID newUserId,  // null = devolver ao estoque
        String reason
) {}
