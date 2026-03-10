package br.dev.ctrls.inovareti.domain.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.auth.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.ResetInitialPasswordRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.usecase.LoginUseCase;
import br.dev.ctrls.inovareti.domain.auth.usecase.ResetInitialPasswordUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para os endpoints de autenticação.
 * Caminho base: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ResetInitialPasswordUseCase resetInitialPasswordUseCase;

    /**
     * Autentica o usuário e retorna um token JWT assinado.
     * Este é o único endpoint público da API.
     *
     * @param request credenciais de login (e-mail + senha)
     * @return 200 OK com o token JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(loginUseCase.execute(request));
    }

    @PostMapping("/reset-initial-password")
    public ResponseEntity<AuthResponseDTO> resetInitialPassword(
            @Valid @RequestBody ResetInitialPasswordRequestDTO request) {
        return ResponseEntity.ok(resetInitialPasswordUseCase.execute(request));
    }
}
