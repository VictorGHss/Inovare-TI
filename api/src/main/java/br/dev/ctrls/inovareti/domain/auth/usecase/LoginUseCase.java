package br.dev.ctrls.inovareti.domain.auth.usecase;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.config.TokenService;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.domain.auth.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsável por autenticar um usuário e retornar um token JWT.
 * Delega a verificação das credenciais ao {@link AuthenticationManager} do Spring Security.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;

    /**
     * Autentica as credenciais informadas e retorna um JWT assinado.
     *
     * @param request as credenciais de login (e-mail + senha)
     * @param ipAddress endereço IP de origem da requisição
     * @return {@link AuthResponseDTO} com o token JWT e os dados do usuário
     */
    public AuthResponseDTO execute(AuthRequestDTO request, String ipAddress) {
        var credentials = new UsernamePasswordAuthenticationToken(
                request.email(), request.password());

        try {
            var authentication = authenticationManager.authenticate(credentials);
            User user = (User) authentication.getPrincipal();

            auditLogService.publish(AuditEvent.of(AuditAction.LOGIN_SUCCESS)
                    .userId(user.getId())
                    .details("{\"email\": \"" + user.getEmail() + "\"}")
                    .ipAddress(ipAddress)
                    .build());

            if (user.isMustChangePassword()) {
                String tempToken = tokenService.generateInitialPasswordResetToken(user);
                log.info("User {} requires initial password reset", user.getEmail());
                return AuthResponseDTO.passwordResetRequired(tempToken, user.getId());
            }

            String token = tokenService.generateToken(user);
            log.info("User {} ({}) successfully authenticated with role: {}",
                    user.getEmail(), user.getName(), user.getRole());
            return AuthResponseDTO.authenticated(token, UserResponseDTO.from(user));

        } catch (BadCredentialsException ex) {
            // Registra falha de autenticação sem expor detalhes ao chamador
            auditLogService.publish(AuditEvent.of(AuditAction.LOGIN_FAILURE)
                    .details("{\"email\": \"" + request.email() + "\"}")
                    .ipAddress(ipAddress)
                    .build());
            log.warn("Failed login attempt for email: {}", request.email());
            throw ex;
        }
    }
}
