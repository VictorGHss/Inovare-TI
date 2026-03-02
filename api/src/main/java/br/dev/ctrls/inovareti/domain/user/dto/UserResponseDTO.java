package br.dev.ctrls.inovareti.domain.user.dto;

import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRole;

import java.util.UUID;

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
        String discordUserId
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
                user.getDiscordUserId()
        );
    }
}
