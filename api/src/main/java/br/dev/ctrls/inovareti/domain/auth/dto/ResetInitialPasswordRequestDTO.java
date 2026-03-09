package br.dev.ctrls.inovareti.domain.auth.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResetInitialPasswordRequestDTO(
        @NotBlank String tempToken,
        @NotNull UUID userId,
        @NotBlank @Size(min = 8, message = "New password must have at least 8 characters") String newPassword
) {}
