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
        if (user == null) {
            return null;
        }
        UUID sectorId = null;
        String sectorName = null;
        if (user.getSector() != null) {
            try {
                sectorId = user.getSector().getId();
                sectorName = user.getSector().getName();
            } catch (Exception ignore) {
                // Previne falha em caso de proxy não inicializado fora da sessão
            }
        }
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                sectorId,
                sectorName,
                user.getLocation(),
                user.getDiscordUserId(),
                user.getContaAzulId(),
                user.isReceivesItNotifications()
        );
    }
}
