package br.dev.ctrls.inovareti.modules.user.application.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;

/**
 * DTO de saída com os dados públicos de um usuário.
 * Nunca expõe {@code passwordHash} nem {@code totpSecret}.
 */
public record UserResponseDTO(
        UUID id,
        String name,
        String email,
        UserRole role,
        UUID sectorId,
        String sectorName,
        String location,
        String discordUserId,
        String contaAzulId,
        @JsonProperty("receives_it_notifications")
        boolean receivesItNotifications
) {
    /** Converte uma entidade {@link User} para este DTO. */
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getSector().getId(),
                user.getSector().getName(),
                user.getLocation(),
                user.getDiscordUserId(),
                user.getContaAzulId(),
                user.isReceivesItNotifications()
        );
    }
}
