package br.dev.ctrls.inovareti.domain.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.ResetInitialPasswordRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorGenerateResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorResetConfirmRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorVerifyRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.usecase.LoginUseCase;
import br.dev.ctrls.inovareti.domain.auth.usecase.ResetInitialPasswordUseCase;
import br.dev.ctrls.inovareti.domain.auth.usecase.TwoFactorAuthService;
import br.dev.ctrls.inovareti.domain.auth.usecase.TwoFactorResetService;
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
    private final TwoFactorAuthService twoFactorAuthService;
    private final TwoFactorResetService twoFactorResetService;

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

    @PostMapping("/2fa/generate")
    public ResponseEntity<TwoFactorGenerateResponseDTO> generateTwoFactor() {
        return ResponseEntity.ok(twoFactorAuthService.generateForUser(getAuthenticatedUserId()));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponseDTO> verifyTwoFactor(@Valid @RequestBody TwoFactorVerifyRequestDTO request) {
        return ResponseEntity.ok(twoFactorAuthService.verifyCode(getAuthenticatedUserId(), request.code()));
    }

    /**
     * Solicita a recuperação do 2FA: gera um código e envia ao Discord do usuário.
     * Requer JWT válido (sem necessidade de 2FA verificado).
     */
    @PostMapping("/2fa/reset-request")
    public ResponseEntity<Void> requestTwoFactorReset() {
        twoFactorResetService.initiateReset(getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirma a recuperação do 2FA: valida o código recebido + senha atual.
     * Se correto, limpa o totp_secret e retorna novo JWT.
     */
    @PostMapping("/2fa/reset-confirm")
    public ResponseEntity<AuthResponseDTO> confirmTwoFactorReset(
            @Valid @RequestBody TwoFactorResetConfirmRequestDTO request) {
        return ResponseEntity.ok(
                twoFactorResetService.confirmReset(getAuthenticatedUserId(), request.code(), request.password()));
    }

    private UUID getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadRequestException("Usuário autenticado não encontrado.");
        }

        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuário autenticado inválido.");
        }
    }
}
