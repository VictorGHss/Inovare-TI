package br.dev.ctrls.inovareti.modules.auth.application.dto;

public record TwoFactorGenerateResponseDTO(
        String qrCodeBase64,
        String otpauthUrl
) {
}
