package br.dev.ctrls.inovareti.domain.asset.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.asset.Asset;

public record AssetResponseDTO(
        UUID id,
        UUID userId,
        String name,
        String patrimonyCode,
        String specifications,
        LocalDateTime createdAt,
        String invoiceFileName
) {
    public static AssetResponseDTO from(Asset asset) {
        return new AssetResponseDTO(
                asset.getId(),
                asset.getUserId(),
                asset.getName(),
                asset.getPatrimonyCode(),
                asset.getSpecifications(),
                asset.getCreatedAt(),
                asset.getInvoiceFileName()
        );
    }
}
