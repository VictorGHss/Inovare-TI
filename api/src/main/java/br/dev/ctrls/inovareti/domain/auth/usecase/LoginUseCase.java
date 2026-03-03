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

/**
 * Use case responsible for authenticating a user and returning a JWT token.
 * Delegates credential verification to the Spring Security {@link AuthenticationManager}.
 */
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
        String token = tokenService.generateToken(user);

        return new AuthResponseDTO(token, UserResponseDTO.from(user));
    }
}
