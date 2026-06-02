package br.dev.ctrls.inovareti.modules.user.application.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: redefine a senha de um usuário para o valor padrão "Mudar@123"
 * e obriga o usuário a trocar a senha no próximo login.
 */
@Component
@RequiredArgsConstructor
public class ResetUserPasswordUseCase {

    private static final String DEFAULT_PASSWORD = "Mudar@123";

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));

        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        userRepository.save(user);

        auditLogService.publish(AuditEvent.of(AuditAction.USER_PASSWORD_ADMIN_RESET)
                .resourceType("User")
                .resourceId(user.getId())
                .details("{\"mode\": \"ADMIN_DEFAULT_RESET\"}")
                .build());
    }
}
