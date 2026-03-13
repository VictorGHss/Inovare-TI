package br.dev.ctrls.inovareti.domain.user.dto;

import br.dev.ctrls.inovareti.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para criação de um usuário.
 * O campo {@code password} recebe a senha em texto puro;
 * o hash é gerado no Use Case antes de persistir.
 */
public record UserRequestDTO(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres.")
        String password,

        @NotNull(message = "O papel (role) é obrigatório.")
        UserRole role,

        @NotNull(message = "O setor é obrigatório.")
        java.util.UUID sectorId,

        @Size(max = 150, message = "A localização deve ter no máximo 150 caracteres.")
        String location,

        @Size(max = 50, message = "O Discord User ID deve ter no máximo 50 caracteres.")
        String discordUserId,

        @com.fasterxml.jackson.annotation.JsonProperty("receives_it_notifications")
        Boolean receivesItNotifications

) {}
