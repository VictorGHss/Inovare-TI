package br.dev.ctrls.inovareti.domain.asset.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.asset.Asset;
import br.dev.ctrls.inovareti.domain.user.User;

public record AssetResponseDTO(
        UUID id,
        UUID userId,
            String assignedToName,
        String name,
        String patrimonyCode,
        String specifications,
        LocalDateTime createdAt,
        String invoiceFileName
) {
    public static AssetResponseDTO from(Asset asset, User assignedToUser) {
        return new AssetResponseDTO(
                asset.getId(),
                asset.getUserId(),
                                assignedToUser != null ? assignedToUser.getName() : null,
                asset.getName(),
                asset.getPatrimonyCode(),
                asset.getSpecifications(),
                asset.getCreatedAt(),
                asset.getInvoiceFileName()
        );
    }
}
