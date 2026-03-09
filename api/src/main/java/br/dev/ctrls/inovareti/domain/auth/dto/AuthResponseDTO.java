package br.dev.ctrls.inovareti.domain.auth.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;

/**
 * Response payload returned after a successful authentication.
 * Includes JWT token and user data.
 */
public record AuthResponseDTO(
		String status,
		String token,
		String tempToken,
		UUID userId,
		UserResponseDTO user
) {
	public static AuthResponseDTO authenticated(String token, UserResponseDTO user) {
		return new AuthResponseDTO("AUTHENTICATED", token, null, user.id(), user);
	}

	public static AuthResponseDTO passwordResetRequired(String tempToken, UUID userId) {
		return new AuthResponseDTO("PASSWORD_RESET_REQUIRED", null, tempToken, userId, null);
	}
}
