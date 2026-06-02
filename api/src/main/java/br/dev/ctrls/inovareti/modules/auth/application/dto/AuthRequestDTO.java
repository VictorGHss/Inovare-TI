package br.dev.ctrls.inovareti.modules.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de requisição para o endpoint de login.
 */
public record AuthRequestDTO(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
