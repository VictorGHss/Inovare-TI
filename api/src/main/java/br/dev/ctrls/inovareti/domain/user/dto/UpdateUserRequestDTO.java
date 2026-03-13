package br.dev.ctrls.inovareti.domain.user.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.dev.ctrls.inovareti.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para atualização dos dados de um usuário existente (nome, e-mail, perfil, setor).
 * A senha é tratada separadamente pelo endpoint reset-password.
 */
public record UpdateUserRequestDTO(

        @NotBlank(message = "Name is required.")
        @Size(max = 150, message = "Name must have at most 150 characters.")
        String name,

        @NotBlank(message = "Email is required.")
        @Email(message = "Provide a valid email.")
        @Size(max = 255, message = "Email must have at most 255 characters.")
        String email,

        @NotNull(message = "Role is required.")
        UserRole role,

        @NotNull(message = "Sector is required.")
        UUID sectorId,

        @JsonProperty("receives_it_notifications")
        Boolean receivesItNotifications
) {}
