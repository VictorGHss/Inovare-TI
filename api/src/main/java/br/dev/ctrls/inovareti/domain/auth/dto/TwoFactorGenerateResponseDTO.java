package br.dev.ctrls.inovareti.domain.auth.dto;

public record TwoFactorGenerateResponseDTO(
        String qrCodeBase64,
        String otpauthUrl
) {
}