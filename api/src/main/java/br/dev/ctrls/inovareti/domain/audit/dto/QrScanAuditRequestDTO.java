package br.dev.ctrls.inovareti.domain.audit.dto;

import jakarta.validation.constraints.NotBlank;

public record QrScanAuditRequestDTO(
        @NotBlank(message = "O caminho escaneado é obrigatório.")
        String scannedPath
) {
}
