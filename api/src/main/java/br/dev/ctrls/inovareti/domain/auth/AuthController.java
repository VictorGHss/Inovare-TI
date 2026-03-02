package br.dev.ctrls.inovareti.domain.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.auth.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.usecase.LoginUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for authentication endpoints.
 * Base path: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    /**
     * Authenticates the user and returns a signed JWT token.
     * This is the only public endpoint in the API.
     *
     * @param request login credentials (email + password)
     * @return 200 OK with the JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(loginUseCase.execute(request));
    }
}
