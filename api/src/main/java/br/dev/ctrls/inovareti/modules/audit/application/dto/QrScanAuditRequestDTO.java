package br.dev.ctrls.inovareti.modules.audit.application.dto;

import jakarta.validation.constraints.NotBlank;

public record QrScanAuditRequestDTO(
        @NotBlank(message = "O caminho escaneado é obrigatório.")
        String scannedPath
) {
}
