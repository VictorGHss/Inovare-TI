package br.dev.ctrls.inovareti.domain.auth.usecase;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.config.TokenService;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case responsible for authenticating a user and returning a JWT token.
 * Delegates credential verification to the Spring Security {@link AuthenticationManager}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    /**
     * Authenticates the given credentials and returns a signed JWT.
     *
     * @param request the login credentials (email + password)
     * @return {@link AuthResponseDTO} containing the JWT token and user data
     */
    public AuthResponseDTO execute(AuthRequestDTO request) {
        var credentials = new UsernamePasswordAuthenticationToken(
                request.email(), request.password());

        var authentication = authenticationManager.authenticate(credentials);
        User user = (User) authentication.getPrincipal();

        if (user.isMustChangePassword()) {
            String tempToken = tokenService.generateInitialPasswordResetToken(user);
            log.info("User {} requires initial password reset", user.getEmail());
            return AuthResponseDTO.passwordResetRequired(tempToken, user.getId());
        }

        String token = tokenService.generateToken(user);

        log.info("User {} ({}) successfully authenticated with role: {}",
                user.getEmail(), user.getName(), user.getRole());

        return AuthResponseDTO.authenticated(token, UserResponseDTO.from(user));
    }
}
