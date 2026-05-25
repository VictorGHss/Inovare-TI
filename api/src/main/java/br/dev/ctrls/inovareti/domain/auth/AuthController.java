package br.dev.ctrls.inovareti.domain.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.ResetInitialPasswordRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorGenerateResponseDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorResetConfirmRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.TwoFactorVerifyRequestDTO;
import br.dev.ctrls.inovareti.config.TokenService;
import br.dev.ctrls.inovareti.domain.auth.usecase.LoginUseCase;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.auth.usecase.ResetInitialPasswordUseCase;
import br.dev.ctrls.inovareti.domain.auth.usecase.TwoFactorAuthService;
import br.dev.ctrls.inovareti.domain.auth.usecase.TwoFactorResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para os endpoints de autenticação.
 * Caminho base: /api/auth
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ResetInitialPasswordUseCase resetInitialPasswordUseCase;
    private final TwoFactorAuthService twoFactorAuthService;
    private final TwoFactorResetService twoFactorResetService;
    private final TokenService tokenService;

    /**
     * Autentica o usuário e retorna um token JWT assinado.
     * Este é o único endpoint público da API.
     *
     * @param request credenciais de login (e-mail + senha)
     * @return 200 OK com o token JWT
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody AuthRequestDTO request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(loginUseCase.execute(request, getClientIp(httpRequest)));
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/reset-initial-password")
    public ResponseEntity<AuthResponseDTO> resetInitialPassword(
            @Valid @RequestBody ResetInitialPasswordRequestDTO request) {
        return ResponseEntity.ok(resetInitialPasswordUseCase.execute(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            tokenService.blacklistToken(token);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/generate")
    public ResponseEntity<TwoFactorGenerateResponseDTO> generateTwoFactor() {
        return ResponseEntity.ok(twoFactorAuthService.generateForUser(getAuthenticatedUserId()));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponseDTO> verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequestDTO request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(twoFactorAuthService.verifyCode(
                getAuthenticatedUserId(),
                request.code(),
                getClientIp(httpRequest)));
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
            @Valid @RequestBody TwoFactorResetConfirmRequestDTO request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                twoFactorResetService.confirmReset(
                        getAuthenticatedUserId(), request.code(), request.password(),
                        getClientIp(httpRequest)));
    }

    private UUID getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadRequestException("Usuário autenticado não encontrado.");
        }

        Object principal = authentication.getPrincipal();

        // Caso o principal seja a entidade User (definida no domínio), retornamos o id
        if (principal instanceof User user) {
            UUID id = user.getId();
            if (id == null) {
                throw new BadRequestException("Identificador do usuário autenticado inválido.");
            }
            return id;
        }

        // Se for uma String contendo o UUID (fluxos antigos), parseamos diretamente
        if (principal instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Identificador do usuário autenticado inválido.");
            }
        }

        // Fallback: tentar converter via toString() sem desreferenciar diretamente
        String principalStr = principal == null ? "" : principal.toString();
        try {
            return UUID.fromString(principalStr);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuário autenticado inválido.");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
