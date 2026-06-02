package br.dev.ctrls.inovareti.modules.auth.application.service;

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
 * Caso de uso responsável por autenticar um usuário e retornar um token JWT.
 * Delega a verificação das credenciais ao AuthenticatorPort da infraestrutura.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUseCase {

    private final AuthenticatorPort authenticator;
    private final TokenPort tokenPort;
    private final AuditLogService auditLogService;

    /**
     * Autentica as credenciais informadas e retorna um JWT assinado.
     *
     * @param request as credenciais de login (e-mail + senha)
     * @param ipAddress endereço IP de origem da requisição
     * @return {@link AuthResponseDTO} com o token JWT e os dados do usuário
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
            // Registra falha de autenticação sem expor detalhes ao chamador
            auditLogService.publish(AuditEvent.of(AuditAction.LOGIN_FAILURE)
                    .details("{\"email\": \"" + request.email() + "\"}")
                    .ipAddress(ipAddress)
                    .build());
            log.warn("Tentativa de login falhada para o e-mail: {}", request.email());
            throw ex;
        }
    }
}
