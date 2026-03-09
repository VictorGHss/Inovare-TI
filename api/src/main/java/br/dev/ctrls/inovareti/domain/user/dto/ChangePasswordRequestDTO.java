package br.dev.ctrls.inovareti.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, message = "New password must have at least 8 characters") String newPassword
) {}
