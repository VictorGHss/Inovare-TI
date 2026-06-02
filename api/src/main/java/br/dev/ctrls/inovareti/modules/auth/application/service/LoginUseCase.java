package br.dev.ctrls.inovareti.modules.auth.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.auth.application.dto.AuthRequestDTO;
import br.dev.ctrls.inovareti.modules.auth.application.dto.AuthResponseDTO;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.AuthenticatorPort;
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.application.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsÃ¡vel por autenticar um usuÃ¡rio e retornar um token JWT.
 * Delega a verificaÃ§Ã£o das credenciais ao AuthenticatorPort da infraestrutura.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class LoginUseCase {

    private final AuthenticatorPort authenticator;
    private final TokenPort tokenPort;
    private final AuditLogService auditLogService;

    /**
     * Autentica as credenciais informadas e retorna um JWT assinado.
     *
     * @param request as credenciais de login (e-mail + senha)
     * @param ipAddress endereÃ§o IP de origem da requisiÃ§Ã£o
     * @return {@link AuthResponseDTO} com o token JWT e os dados do usuÃ¡rio
     */
    public AuthResponseDTO execute(AuthRequestDTO request, String ipAddress) {
        try {
            User user = authenticator.authenticate(request.email(), request.password());

            auditLogService.publish(AuditEvent.of(AuditAction.LOGIN_SUCCESS)
                    .userId(user.getId())
                    .details("{\"email\": \"" + user.getEmail() + "\"}")
                    .ipAddress(ipAddress)
                    .build());

            if (user.isMustChangePassword()) {
                String tempToken = tokenPort.generateInitialPasswordResetToken(user);
                log.info("User {} requires initial password reset", user.getEmail());
                return AuthResponseDTO.passwordResetRequired(tempToken, user.getId());
            }

            String token = tokenPort.generateToken(user);
            log.info("User {} ({}) successfully authenticated with role: {}",
                    user.getEmail(), user.getName(), user.getRole());
            return AuthResponseDTO.authenticated(token, UserResponseDTO.from(user));

        } catch (BadCredentialsException ex) {
            // Registra falha de autenticaÃ§Ã£o sem expor detalhes ao chamador
            auditLogService.publish(AuditEvent.of(AuditAction.LOGIN_FAILURE)
                    .details("{\"email\": \"" + request.email() + "\"}")
                    .ipAddress(ipAddress)
                    .build());
            log.warn("Tentativa de login falhada para o e-mail: {}", request.email());
            throw ex;
        }
    }
}


