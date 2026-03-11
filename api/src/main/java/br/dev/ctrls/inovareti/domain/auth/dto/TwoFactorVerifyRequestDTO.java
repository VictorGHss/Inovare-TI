package br.dev.ctrls.inovareti.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorVerifyRequestDTO(
        @NotBlank(message = "O código 2FA é obrigatório.")
        @Pattern(regexp = "^\\d{6}$", message = "O código 2FA deve conter 6 dígitos numéricos.")
        String code
) {
}