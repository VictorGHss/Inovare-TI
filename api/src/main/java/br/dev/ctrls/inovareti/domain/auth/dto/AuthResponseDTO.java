package br.dev.ctrls.inovareti.domain.auth.dto;

import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;

/**
 * Response payload returned after a successful authentication.
 * Includes JWT token and user data.
 */
public record AuthResponseDTO(String token, UserResponseDTO user) {}
