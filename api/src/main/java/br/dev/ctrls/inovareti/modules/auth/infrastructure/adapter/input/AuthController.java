package br.dev.ctrls.inovareti.modules.auth.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.auth.application.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.ResetInitialPasswordRequestDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.TwoFactorGenerateResponseDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.TwoFactorResetConfirmRequestDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.TwoFactorVerifyRequestDTO;
import br.dev.ctrls.inovareti.modules.auth.application.service.LoginUseCase;
import br.dev.ctrls.inovareti.modules.auth.application.service.ResetInitialPasswordUseCase;
import br.dev.ctrls.inovareti.modules.auth.application.service.TwoFactorAuthService;
import br.dev.ctrls.inovareti.modules.auth.application.service.TwoFactorResetService;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para os endpoints de autenticaÃ§Ã£o.
 * Caminho base: /api/auth
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Observed
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ResetInitialPasswordUseCase resetInitialPasswordUseCase;
    private final TwoFactorAuthService twoFactorAuthService;
    private final TwoFactorResetService twoFactorResetService;
    private final TokenPort tokenPort;

    /**
     * Autentica o usuÃ¡rio e retorna um token JWT assinado.
     * Este Ã© o Ãºnico endpoint pÃºblico da API.
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
            tokenPort.blacklistToken(token);
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
     * Solicita a recuperaÃ§Ã£o do 2FA: gera um cÃ³digo e envia ao Discord do usuÃ¡rio.
     * Requer JWT vÃ¡lido (sem necessidade de 2FA verificado).
     */
    @PostMapping("/2fa/reset-request")
    public ResponseEntity<Void> requestTwoFactorReset() {
        twoFactorResetService.initiateReset(getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirma a recuperaÃ§Ã£o do 2FA: valida o cÃ³digo recebido + senha atual.
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
            throw new BadRequestException("UsuÃ¡rio autenticado nÃ£o encontrado.");
        }

        Object principal = authentication.getPrincipal();

        // Caso o principal seja a entidade User (definida no domÃ­nio), retornamos o id
        if (principal instanceof User user) {
            UUID id = user.getId();
            if (id == null) {
                throw new BadRequestException("Identificador do usuÃ¡rio autenticado invÃ¡lido.");
            }
            return id;
        }

        // Se for uma String contendo o UUID (fluxos antigos), parseamos diretamente
        if (principal instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Identificador do usuÃ¡rio autenticado invÃ¡lido.");
            }
        }

        // Fallback: tentar converter via toString() sem desreferenciar diretamente
        String principalStr = principal == null ? "" : principal.toString();
        try {
            return UUID.fromString(principalStr);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuÃ¡rio autenticado invÃ¡lido.");
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


