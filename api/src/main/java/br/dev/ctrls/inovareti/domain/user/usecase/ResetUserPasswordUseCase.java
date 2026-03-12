package br.dev.ctrls.inovareti.domain.user.usecase;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: redefine a senha de um usuário para o valor padrão "Mudar@123"
 * e obriga o usuário a trocada de senha no próximo login.
 */
@Component
@RequiredArgsConstructor
public class ResetUserPasswordUseCase {

    private static final String DEFAULT_PASSWORD = "Mudar@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        userRepository.save(user);

        auditLogService.publish(AuditEvent.of(AuditAction.USER_PASSWORD_RESET)
            .resourceType("User")
            .resourceId(user.getId())
            .details("{\"mode\": \"ADMIN_DEFAULT_RESET\"}")
            .build());
    }
}
