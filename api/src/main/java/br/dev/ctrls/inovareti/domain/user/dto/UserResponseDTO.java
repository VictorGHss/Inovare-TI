package br.dev.ctrls.inovareti.domain.user.dto;

import br.dev.ctrls.inovareti.domain.user.UserRole;

/**
 * DTO de saída com os dados públicos de um usuário.
 * Nunca expõe {@code passwordHash} nem {@code totpSecret}.
 */
public record UserResponseDTO(
        java.util.UUID id,
        String name,
        String email,
        UserRole role,
        java.util.UUID sectorId,
        String sectorName,
        String location,
        String discordUserId,
        @com.fasterxml.jackson.annotation.JsonProperty("receives_it_notifications")
        boolean receivesItNotifications
) {
    /** Converte uma entidade {@link br.dev.ctrls.inovareti.domain.user.User} para este DTO. */
    public static UserResponseDTO from(br.dev.ctrls.inovareti.domain.user.User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getSector().getId(),
                user.getSector().getName(),
                user.getLocation(),
                user.getDiscordUserId(),
                user.isReceivesItNotifications()
        );
    }
}
