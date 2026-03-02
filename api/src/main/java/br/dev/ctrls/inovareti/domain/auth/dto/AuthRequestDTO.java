package br.dev.ctrls.inovareti.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for the login endpoint.
 */
public record AuthRequestDTO(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
