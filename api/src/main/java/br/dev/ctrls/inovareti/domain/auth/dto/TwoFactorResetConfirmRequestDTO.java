package br.dev.ctrls.inovareti.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorResetConfirmRequestDTO(
        @NotBlank(message = "O código de recuperação é obrigatório.") String code,
        @NotBlank(message = "A senha atual é obrigatória.") String password) {
}
